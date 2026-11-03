package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.PackManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Auswahl der Textur aus dem Resource Pack. */
public final class TextureMenu extends Menu {
    private final CustomItem ci;
    private final int page;
    private final int pages;
    private final List<String> names;

    public TextureMenu(AciPlugin plugin, CustomItem ci, int page) {
        super(plugin);
        this.ci = ci;
        this.names = plugin.pack().textureNames();
        this.pages = Math.max(1, (names.size() + 44) / 45);
        this.page = Math.min(Math.max(page, 0), pages - 1);
        inv = Bukkit.createInventory(this, 54, Ui.text("&8Choose texture &7(" + (this.page + 1) + "/" + pages + ")"));

        for (int i = 0; i < 45; i++) {
            int idx = this.page * 45 + i;
            if (idx >= names.size()) break;
            String n = names.get(idx);
            boolean current = n.equals(ci.texture);
            ItemStack st = new ItemStack(Material.AMETHYST_SHARD);
            ItemMeta m = st.getItemMeta();
            m.setItemModel(new NamespacedKey(PackManager.NAMESPACE, n));
            m.displayName(Ui.text((current ? "&a" : "&f") + n));
            m.lore(List.of(Ui.text(current ? "&2Currently selected" : "&7Click to select")));
            if (current) m.setEnchantmentGlintOverride(true);
            st.setItemMeta(m);
            inv.setItem(i, st);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, Ui.pane());
        inv.setItem(45, Ui.button(Material.ARROW, "&cBack to editor"));
        boolean noneSelected = ci.texture == null || ci.texture.isBlank();
        ItemStack noneIcon = new ItemStack(ci.material);
        Ui.addLore(noneIcon, (noneSelected ? "&a" : "&f") + "Use the item's own texture",
                "&7No custom texture - shows", "&7" + ci.material.name() + " as normal.",
                "", noneSelected ? "&2Currently selected" : "&7Click to select");
        inv.setItem(46, noneIcon);
        if (this.page > 0) inv.setItem(48, Ui.button(Material.ARROW, "&ePrevious page"));
        if (this.page < pages - 1) inv.setItem(50, Ui.button(Material.ARROW, "&eNext page"));
        inv.setItem(49, Ui.button(Material.PAPER, "&6Custom textures",
                "&7Put a PNG file in &fplugins/ACI/textures/",
                "&7(file name = texture name, square,",
                "&7e.g. 16x16 or 32x32), then click",
                "&7&bReload & send pack&7 in the main menu."));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot < 45) {
            int idx = page * 45 + slot;
            if (idx >= names.size()) return;
            ci.texture = names.get(idx);
            plugin.items().changed();
            new ItemEditorMenu(plugin, ci).open(p);
        } else if (slot == 46) {
            ci.texture = null;
            plugin.items().changed();
            new ItemEditorMenu(plugin, ci).open(p);
        } else if (slot == 45) {
            new ItemEditorMenu(plugin, ci).open(p);
        } else if (slot == 48 && page > 0) {
            new TextureMenu(plugin, ci, page - 1).open(p);
        } else if (slot == 50 && page < pages - 1) {
            new TextureMenu(plugin, ci, page + 1).open(p);
        }
    }
}
