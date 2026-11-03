package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.CustomRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** All recipes of one item (admin view). */
public final class RecipeListMenu extends Menu {
    private static final int MAX_RECIPES = 18;
    private final CustomItem ci;

    public RecipeListMenu(AciPlugin plugin, CustomItem ci) {
        super(plugin);
        this.ci = ci;
        inv = Bukkit.createInventory(this, 27, Ui.text("&8Recipes: &0" + Ui.plain(ci.name)));
        for (int i = 18; i < 27; i++) inv.setItem(i, Ui.pane());

        for (int i = 0; i < ci.recipes.size() && i < MAX_RECIPES; i++) {
            CustomRecipe r = ci.recipes.get(i);
            ItemStack st = plugin.items().create(ci, r.amount);
            Ui.addLore(st, "", "&6Recipe " + (i + 1),
                    "&eLeft click: &7edit",
                    "&eShift-right click: &7delete");
            inv.setItem(i, st);
        }
        inv.setItem(18, Ui.button(Material.ARROW, "&cBack to editor"));
        inv.setItem(22, Ui.button(Material.LIME_DYE, "&aNew recipe",
                "&7Place the required items into the", "&73x3 grid."));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot < MAX_RECIPES) {
            if (slot >= ci.recipes.size()) return;
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                ci.recipes.remove(slot);
                plugin.items().changed();
                new RecipeListMenu(plugin, ci).open(p);
            } else {
                new RecipeEditorMenu(plugin, ci, slot).open(p);
            }
        } else if (slot == 18) {
            new ItemEditorMenu(plugin, ci).open(p);
        } else if (slot == 22) {
            if (ci.recipes.size() >= MAX_RECIPES) {
                p.sendMessage(Ui.text("&c[ACI] Maximum " + MAX_RECIPES + " recipes per item."));
                return;
            }
            new RecipeEditorMenu(plugin, ci, ci.recipes.size()).open(p);
        }
    }
}
