package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

/** Editor for a single custom item. */
public final class ItemEditorMenu extends Menu {
    private final CustomItem ci;

    public ItemEditorMenu(AciPlugin plugin, CustomItem ci) {
        super(plugin);
        this.ci = ci;
        inv = Bukkit.createInventory(this, 27, Ui.text("&8Item: &0" + Ui.plain(ci.name)));
        for (int i = 0; i < 27; i++) inv.setItem(i, Ui.pane());

        inv.setItem(4, plugin.items().create(ci, 1));
        inv.setItem(10, Ui.button(Material.NAME_TAG, "&eChange name",
                "&7Current: &r" + ci.name, "&8MiniMessage, e.g.:", "&8\\<gradient:#ff4500:#ffd000>Name\\</gradient>", "&8Old &-codes also work."));
        inv.setItem(11, Ui.button(Material.PAINTING, "&eChoose texture",
                "&7Current: &f" + (ci.texture == null || ci.texture.isBlank() ? "(own vanilla texture)" : ci.texture)));
        inv.setItem(12, Ui.button(Material.WRITABLE_BOOK, "&eDescription (lore)",
                "&7Lines: &f" + ci.lore.size(),
                "&eClick: &7add a line",
                "&eShift-click: &7remove the last line"));
        inv.setItem(13, Ui.button(Material.POTION, "&eEffects",
                "&7Count: &f" + ci.effects.size(),
                "&7Permanent effects while worn", "&7(e.g. in the off hand)."));
        inv.setItem(14, Ui.button(Material.CRAFTING_TABLE, "&eRecipes",
                "&7Count: &f" + ci.recipes.size()));
        inv.setItem(15, Ui.addLore(new ItemStack(ci.material),
                "&7", "&eBase item: &f" + ci.material.name()
                        + (ci.potionType != null ? " (" + ci.potionType.getKey().getKey() + ")" : ""),
                "&7Click with another item",
                "&7on your cursor to change it."));
        inv.setItem(22, Ui.button(Material.COMPARATOR, "&bAttributes, cooldown & other settings",
                "&7Cooldown: &f" + ci.cooldownSeconds + " s",
                "&7Auto offhand: &f" + (ci.autoOffhand ? "on" : "off"),
                "&7Attributes: &f" + ci.attributes.size(),
                "&7Enchants: &f" + ci.enchantments.size(),
                "&7Shulker rows: &f" + ci.shulkerRows));
        inv.setItem(17, Ui.button(Material.OAK_SIGN, "&eBase item: pick by name",
                "&7Type a vanilla item name, e.g.", "&7'iron_ingot', or a potion type,",
                "&7e.g. 'potion:fire_resistance' or", "&7'splash_potion:strength'."));
        inv.setItem(16, Ui.button(Material.CHEST, "&aGet item",
                "&eClick: &71x", "&eShift-click: &7full stack"));
        inv.setItem(18, Ui.button(Material.ARROW, "&cBack"));
        inv.setItem(26, Ui.button(Material.TNT, "&4Delete item",
                "&cShift-click to confirm!"));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        switch (e.getSlot()) {
            case 10 -> plugin.prompts().ask(p, "New display name for " + ci.id + " (MiniMessage, e.g. \\<gradient:#ff4500:#ffd000>Fire Crystal\\</gradient> or \\<bold>\\<red>Name):",
                    text -> {
                        ci.name = text;
                        plugin.items().changed();
                        new ItemEditorMenu(plugin, ci).open(p);
                    },
                    () -> new ItemEditorMenu(plugin, ci).open(p));
            case 11 -> new TextureMenu(plugin, ci, 0).open(p);
            case 12 -> {
                if (e.isShiftClick()) {
                    if (!ci.lore.isEmpty()) ci.lore.remove(ci.lore.size() - 1);
                    plugin.items().changed();
                    new ItemEditorMenu(plugin, ci).open(p);
                } else {
                    plugin.prompts().ask(p, "New lore line (MiniMessage or &-codes):",
                            text -> {
                                ci.lore.add(text);
                                plugin.items().changed();
                                new ItemEditorMenu(plugin, ci).open(p);
                            },
                            () -> new ItemEditorMenu(plugin, ci).open(p));
                }
            }
            case 13 -> new EffectsMenu(plugin, ci, 0).open(p);
            case 14 -> new RecipeListMenu(plugin, ci).open(p);
            case 22 -> new SettingsMenu(plugin, ci).open(p);
            case 15 -> {
                ItemStack cursor = e.getCursor();
                if (cursor == null || cursor.getType().isAir()) return;
                if (plugin.items().idOf(cursor) != null || !cursor.getType().isItem()) {
                    p.sendMessage(Ui.text("&c[ACI] Only a plain vanilla item can be used as a base."));
                    return;
                }
                applyBase(cursor.getType(), cursor.getItemMeta() instanceof PotionMeta pm ? pm.getBasePotionType() : null);
                new ItemEditorMenu(plugin, ci).open(p);
            }
            case 17 -> plugin.prompts().ask(p,
                    "Base item name (e.g. 'iron_ingot' or 'potion:fire_resistance'):",
                    text -> {
                        String token = plugin.items().tokenFromName(text);
                        if (token == null || token.startsWith("aci:")) {
                            p.sendMessage(Ui.text("&c[ACI] Unknown item/potion: " + text));
                        } else {
                            ItemStack parsed = plugin.items().stackForToken(token, 1);
                            applyBase(parsed.getType(), parsed.getItemMeta() instanceof PotionMeta pm ? pm.getBasePotionType() : null);
                        }
                        new ItemEditorMenu(plugin, ci).open(p);
                    },
                    () -> new ItemEditorMenu(plugin, ci).open(p));
            case 16 -> plugin.items().give(p, ci, e.isShiftClick() ? ci.material.getMaxStackSize() : 1);
            case 18 -> new AdminMainMenu(plugin, 0).open(p);
            case 26 -> {
                if (!e.isShiftClick()) {
                    p.sendMessage(Ui.text("&c[ACI] Shift-click to delete."));
                    return;
                }
                plugin.items().remove(ci);
                p.sendMessage(Ui.text("&a[ACI] Item " + ci.id + " deleted."));
                new AdminMainMenu(plugin, 0).open(p);
            }
            default -> { }
        }
    }

    private void applyBase(Material material, org.bukkit.potion.PotionType potionType) {
        ci.material = material;
        ci.potionType = ItemManager.isPotionMaterial(material) ? potionType : null;
        plugin.items().changed();
    }
}
