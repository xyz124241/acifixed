package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.ItemManager.RecipeRef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /ci - overview of all recipes (for every player). Click opens that recipe's page. */
public final class RecipeIndexMenu extends Menu {
    private final int page;
    private final int pages;
    private final List<RecipeRef> refs;

    public RecipeIndexMenu(AciPlugin plugin, int page) {
        super(plugin);
        this.refs = plugin.items().refs();
        this.pages = Math.max(1, (refs.size() + 44) / 45);
        this.page = Math.min(Math.max(page, 0), pages - 1);
        inv = Bukkit.createInventory(this, 54, Ui.text("&8Recipe book &7(" + (this.page + 1) + "/" + pages + ")"));

        for (int i = 0; i < 45; i++) {
            int idx = this.page * 45 + i;
            if (idx >= refs.size()) break;
            RecipeRef ref = refs.get(idx);
            ItemStack st = plugin.items().create(ref.item(), ref.item().recipes.get(ref.index()).amount);
            Ui.addLore(st, "", "&6Recipe " + (idx + 1) + " of " + refs.size(), "&eClick: &7view recipe");
            inv.setItem(i, st);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, Ui.pane());
        if (this.page > 0) inv.setItem(45, Ui.button(Material.ARROW, "&ePrevious page"));
        if (this.page < pages - 1) inv.setItem(53, Ui.button(Material.ARROW, "&eNext page"));
        inv.setItem(49, Ui.button(Material.BOOK, "&6Recipe book",
                refs.isEmpty() ? "&7No recipes yet." : "&7" + refs.size() + " recipes"));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot < 45) {
            int idx = page * 45 + slot;
            if (idx < refs.size()) new RecipeBookMenu(plugin, idx).open(p);
        } else if (slot == 45 && page > 0) {
            new RecipeIndexMenu(plugin, page - 1).open(p);
        } else if (slot == 53 && page < pages - 1) {
            new RecipeIndexMenu(plugin, page + 1).open(p);
        }
    }
}
