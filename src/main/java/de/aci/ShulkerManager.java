package de.aci;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Persistent virtual shulker storage with configurable 1-6 rows. */
public final class ShulkerManager implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final AciPlugin plugin;
    private final Map<String, Integer> openAt = new HashMap<>();
    private final NamespacedKey marker, rowsKey, pickupKey, dataKey, itemIdKey, customNameKey;

    public ShulkerManager(AciPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "aci_shulker");
        rowsKey = new NamespacedKey(plugin, "shulker_rows");
        pickupKey = new NamespacedKey(plugin, "shulker_auto_pickup");
        dataKey = new NamespacedKey(plugin, "shulker_contents");
        itemIdKey = new NamespacedKey(plugin, "id");
        customNameKey = new NamespacedKey(plugin, "shulker_custom_name");
    }

    /** Keep ACI metadata when a player renames a custom shulker in an anvil. */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack input = event.getInventory().getItem(0);
        ItemStack result = event.getResult();
        if (!isAciShulker(input) || result == null) return;
        event.setResult(copyShulkerMetadata(input, result, false));
    }

    /** Vanilla dyeing changes the box material; retain its ACI id, name and contents. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !ItemManager.isShulkerMaterial(result.getType())) return;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isAciShulker(ingredient)) {
                event.getInventory().setResult(copyShulkerMetadata(ingredient, result, true));
                return;
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (!isAciShulker(item)) return;
        ItemMeta itemMeta = item.getItemMeta();
        PersistentDataContainer from = itemMeta.getPersistentDataContainer();
        String id = from.get(itemIdKey, PersistentDataType.STRING);
        CustomItem ci = plugin.items().get(id);
        if (ci == null) return;

        Block block = e.getBlockPlaced();
        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer to = tile.getPersistentDataContainer();
        int rows = from.getOrDefault(rowsKey, PersistentDataType.INTEGER, ci.shulkerRows);
        byte pickup = from.getOrDefault(pickupKey, PersistentDataType.BYTE, (byte) (ci.shulkerAutoPickup ? 1 : 0));
        to.set(marker, PersistentDataType.BYTE, (byte) 1);
        to.set(rowsKey, PersistentDataType.INTEGER, Math.max(1, Math.min(6, rows)));
        to.set(pickupKey, PersistentDataType.BYTE, pickup);
        to.set(itemIdKey, PersistentDataType.STRING, id);
        byte[] stored = from.get(dataKey, PersistentDataType.BYTE_ARRAY);
        to.set(dataKey, PersistentDataType.BYTE_ARRAY,
                stored == null ? serialize(new ItemStack[Math.max(1, Math.min(6, rows)) * 9]) : stored);
        if (itemMeta.displayName() != null) {
            to.set(customNameKey, PersistentDataType.STRING, LEGACY.serialize(itemMeta.displayName()));
        }
        tile.update(true, false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block block = e.getClickedBlock();
        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (!pdc.has(marker, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (openAt.getOrDefault(key(block.getLocation()), 0) > 0) {
            e.getPlayer().sendMessage("§cDiese Custom-Shulker ist bereits geöffnet.");
            return;
        }
        int rows = Math.max(1, Math.min(6, pdc.getOrDefault(rowsKey, PersistentDataType.INTEGER, 3)));
        VirtualHolder holder = new VirtualHolder(block.getLocation(), rows);
        holder.inventory.setContents(deserialize(pdc.get(dataKey, PersistentDataType.BYTE_ARRAY), rows * 9));
        openAt.merge(key(block.getLocation()), 1, Integer::sum);
        e.getPlayer().openInventory(holder.inventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof VirtualHolder holder)) return;
        openAt.computeIfPresent(key(holder.location), (k, v) -> v <= 1 ? null : v - 1);
        Block block = holder.location.getBlock();
        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (!pdc.has(marker, PersistentDataType.BYTE)) return;
        pdc.set(dataKey, PersistentDataType.BYTE_ARRAY, serialize(holder.inventory.getContents()));
        tile.update(true, false);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (!pdc.has(marker, PersistentDataType.BYTE)) return;
        if (openAt.getOrDefault(key(block.getLocation()), 0) > 0) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cSchließe zuerst die Custom-Shulker.");
            return;
        }

        e.setCancelled(true);
        e.setDropItems(false);
        e.setExpToDrop(0);
        int rows = Math.max(1, Math.min(6, pdc.getOrDefault(rowsKey, PersistentDataType.INTEGER, 3)));
        boolean pickup = pdc.getOrDefault(pickupKey, PersistentDataType.BYTE, (byte) 1) == 1;
        byte[] data = pdc.get(dataKey, PersistentDataType.BYTE_ARRAY);
        String id = pdc.get(itemIdKey, PersistentDataType.STRING);
        String savedName = pdc.get(customNameKey, PersistentDataType.STRING);
        CustomItem ci = plugin.items().get(id);

        // Use the placed block's material so dye color survives place -> break.
        ItemStack drop = new ItemStack(block.getType());
        if (ci != null) {
            ItemStack template = plugin.items().create(ci, 1);
            if (template.hasItemMeta()) drop.setItemMeta(template.getItemMeta());
        }
        ItemMeta meta = drop.getItemMeta();
        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        itemPdc.set(marker, PersistentDataType.BYTE, (byte) 1);
        itemPdc.set(rowsKey, PersistentDataType.INTEGER, rows);
        itemPdc.set(pickupKey, PersistentDataType.BYTE, (byte) (pickup ? 1 : 0));
        itemPdc.set(dataKey, PersistentDataType.BYTE_ARRAY,
                data == null ? serialize(new ItemStack[rows * 9]) : data);
        if (id != null) itemPdc.set(itemIdKey, PersistentDataType.STRING, id);
        if (savedName != null && !savedName.isBlank()) {
            meta.displayName(LEGACY.deserialize(savedName));
        }
        addContentsTooltip(meta, deserialize(data, rows * 9));
        drop.setItemMeta(meta);

        block.setType(Material.AIR, false);
        if (pickup) {
            Map<Integer, ItemStack> rest = e.getPlayer().getInventory().addItem(drop);
            rest.values().forEach(r -> block.getWorld().dropItemNaturally(block.getLocation(), r));
        } else {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }

    @EventHandler
    public void onVirtualInventoryClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof VirtualHolder)) return;
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        if ((cursor != null && ItemManager.isShulkerMaterial(cursor.getType()))
                || (current != null && ItemManager.isShulkerMaterial(current.getType()))) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p)
                p.sendMessage("§cShulker-Boxen können nicht ineinander gelagert werden.");
        }
    }

    @EventHandler
    public void onVirtualInventoryDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof VirtualHolder)) return;
        ItemStack cursor = e.getOldCursor();
        if (cursor != null && ItemManager.isShulkerMaterial(cursor.getType())) e.setCancelled(true);
    }

    /** Persist open virtual inventories before the plugin is disabled or reloaded. */
    public void saveAndCloseOpenInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof VirtualHolder holder)) continue;
            Block block = holder.location.getBlock();
            if (block.getState() instanceof TileState tile) {
                PersistentDataContainer pdc = tile.getPersistentDataContainer();
                if (pdc.has(marker, PersistentDataType.BYTE)) {
                    pdc.set(dataKey, PersistentDataType.BYTE_ARRAY, serialize(holder.inventory.getContents()));
                    tile.update(true, false);
                }
            }
            player.closeInventory();
        }
        openAt.clear();
    }

    private boolean isAciShulker(ItemStack item) {
        if (item == null || !ItemManager.isShulkerMaterial(item.getType()) || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    private ItemStack copyShulkerMetadata(ItemStack source, ItemStack result, boolean copyName) {
        if (!source.hasItemMeta() || !result.hasItemMeta()) return result;
        ItemMeta sourceMeta = source.getItemMeta();
        ItemMeta resultMeta = result.getItemMeta();
        PersistentDataContainer from = sourceMeta.getPersistentDataContainer();
        PersistentDataContainer to = resultMeta.getPersistentDataContainer();
        copyByte(from, to, marker);
        copyInt(from, to, rowsKey);
        copyByte(from, to, pickupKey);
        copyBytes(from, to, dataKey);
        copyString(from, to, itemIdKey);
        copyString(from, to, customNameKey);
        if (copyName || resultMeta.displayName() == null) {
            if (sourceMeta.displayName() != null) resultMeta.displayName(sourceMeta.displayName());
        }
        if (sourceMeta.lore() != null) resultMeta.lore(new ArrayList<>(sourceMeta.lore()));
        result.setItemMeta(resultMeta);
        return result;
    }

    private static void copyByte(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Byte value = from.get(key, PersistentDataType.BYTE);
        if (value != null) to.set(key, PersistentDataType.BYTE, value);
    }
    private static void copyInt(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Integer value = from.get(key, PersistentDataType.INTEGER);
        if (value != null) to.set(key, PersistentDataType.INTEGER, value);
    }
    private static void copyBytes(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        byte[] value = from.get(key, PersistentDataType.BYTE_ARRAY);
        if (value != null) to.set(key, PersistentDataType.BYTE_ARRAY, value);
    }
    private static void copyString(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        String value = from.get(key, PersistentDataType.STRING);
        if (value != null) to.set(key, PersistentDataType.STRING, value);
    }

    private void addContentsTooltip(ItemMeta meta, ItemStack[] contents) {
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Inhalt:", NamedTextColor.GOLD));
        int shown = 0;
        int total = 0;
        for (ItemStack stack : contents) if (stack != null && !stack.getType().isAir()) total++;
        if (total == 0) {
            lore.add(Component.text("Leer", NamedTextColor.GRAY));
        } else {
            for (ItemStack stack : contents) {
                if (stack == null || stack.getType().isAir()) continue;
                if (shown >= 24) break;
                String itemName;
                if (stack.hasItemMeta() && stack.getItemMeta().displayName() != null) {
                    itemName = LEGACY.serialize(stack.getItemMeta().displayName());
                } else {
                    itemName = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                }
                lore.add(LEGACY.deserialize("§7- §f" + stack.getAmount() + "x " + itemName));
                shown++;
            }
            if (total > shown) lore.add(Component.text("… und " + (total - shown) + " weitere", NamedTextColor.GRAY));
        }
        meta.lore(lore);
    }

    private static String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static byte[] serialize(ItemStack[] items) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream o = new BukkitObjectOutputStream(b)) { o.writeObject(items); }
            return b.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private static ItemStack[] deserialize(byte[] bytes, int size) {
        if (bytes == null || bytes.length == 0) return new ItemStack[size];
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = in.readObject();
            if (obj instanceof ItemStack[] a) {
                ItemStack[] out = new ItemStack[size];
                System.arraycopy(a, 0, out, 0, Math.min(size, a.length));
                return out;
            }
        } catch (Exception ignored) { }
        return new ItemStack[size];
    }

    private static final class VirtualHolder implements InventoryHolder {
        private final Location location;
        private final Inventory inventory;
        private VirtualHolder(Location location, int rows) {
            this.location = location.clone();
            this.inventory = Bukkit.createInventory(this, rows * 9, "ACI Shulker");
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
