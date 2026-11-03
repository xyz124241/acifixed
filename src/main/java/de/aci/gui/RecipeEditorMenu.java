package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.CustomRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Recipe editor: the 3x3 grid is made of "ghost slots". Click into it with an item on your
 * cursor to set that ingredient (the cursor itself is untouched, nothing is consumed); click
 * with an empty cursor to clear the slot. Shift-click in your own inventory puts the item into
 * the first free slot. Custom items and normal items both work, and the "pick by name" button
 * lets you set an ingredient you don't physically have (e.g. a specific potion).
 */
public final class RecipeEditorMenu extends Menu {
    static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 25, ARROW = 23, AMT_UP = 16, AMT_DOWN = 34, SAVE = 40, BACK = 36, PICK = 7;

    private final CustomItem ci;
    private final int index;
    private int amount;

    public RecipeEditorMenu(AciPlugin plugin, CustomItem ci, int index) {
        super(plugin);
        this.ci = ci;
        this.index = index;
        inv = Bukkit.createInventory(this, 45, Ui.text("&8Recipe " + (index + 1) + ": &0" + Ui.plain(ci.name)));
        for (int i = 0; i < 45; i++) inv.setItem(i, Ui.pane());

        CustomRecipe r = index < ci.recipes.size() ? ci.recipes.get(index) : new CustomRecipe();
        amount = r.amount;
        for (int i = 0; i < 9; i++) inv.setItem(GRID[i], r.grid[i] == null ? null : plugin.items().stackForToken(r.grid[i], 1));

        inv.setItem(ARROW, Ui.button(Material.ARROW, "&7yields"));
        inv.setItem(AMT_UP, Ui.button(Material.LIME_STAINED_GLASS_PANE, "&a+1 amount", "&7Shift: +8"));
        inv.setItem(AMT_DOWN, Ui.button(Material.RED_STAINED_GLASS_PANE, "&c-1 amount", "&7Shift: -8"));
        inv.setItem(BACK, Ui.button(Material.BARRIER, "&cCancel", "&7Discard changes"));
        inv.setItem(SAVE, Ui.button(Material.EMERALD_BLOCK, "&aSave recipe"));
        inv.setItem(PICK, Ui.button(Material.OAK_SIGN, "&ePick ingredient by name",
                "&7Type an item name (e.g. 'iron_ingot'),",
                "&7a potion (e.g. 'potion:fire_resistance')",
                "&7or a custom item id - fills the first",
                "&7empty grid slot."));
        inv.setItem(4, Ui.button(Material.PAPER, "&6How this works",
                "&7Take an item on your cursor and click",
                "&7a grid slot to set that ingredient.",
                "&7Empty cursor + click = clear the slot.",
                "&7Shift-click in your inventory also works.",
                "&7(Nothing is actually consumed here.)"));
        updateResult();
    }

    private void updateResult() {
        inv.setItem(RESULT, plugin.items().create(ci, amount));
    }

    private static int gridIndex(int slot) {
        for (int i = 0; i < 9; i++) if (GRID[i] == slot) return i;
        return -1;
    }

    private void setGhost(int slot, ItemStack source) {
        String token = plugin.items().tokenFor(source);
        inv.setItem(slot, token == null ? null : plugin.items().stackForToken(token, 1));
    }

    private boolean fillFirstEmpty(String token) {
        for (int slot : GRID) {
            ItemStack cur = inv.getItem(slot);
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(slot, plugin.items().stackForToken(token, 1));
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        int g = gridIndex(slot);

        if (g >= 0) {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType().isAir()) inv.setItem(slot, null);
            else setGhost(slot, cursor);
            return;
        }

        switch (slot) {
            case AMT_UP -> {
                amount = Math.min(64, amount + (e.isShiftClick() ? 8 : 1));
                updateResult();
            }
            case AMT_DOWN -> {
                amount = Math.max(1, amount - (e.isShiftClick() ? 8 : 1));
                updateResult();
            }
            case PICK -> plugin.prompts().ask(p,
                    "Ingredient name (e.g. 'iron_ingot', 'potion:fire_resistance', or a custom item id):",
                    text -> {
                        String token = plugin.items().tokenFromName(text);
                        if (token == null) {
                            p.sendMessage(Ui.text("&c[ACI] Unknown item/potion: " + text));
                        } else if (!fillFirstEmpty(token)) {
                            p.sendMessage(Ui.text("&c[ACI] The grid is full."));
                        }
                        new RecipeEditorMenu(plugin, ci, index).open(p);
                    },
                    () -> new RecipeEditorMenu(plugin, ci, index).open(p));
            case BACK -> new RecipeListMenu(plugin, ci).open(p);
            case SAVE -> {
                CustomRecipe r = new CustomRecipe();
                r.amount = amount;
                for (int i = 0; i < 9; i++) r.grid[i] = plugin.items().tokenFor(inv.getItem(GRID[i]));
                if (r.isEmpty()) {
                    p.sendMessage(Ui.text("&c[ACI] The grid is empty."));
                    return;
                }
                if (index < ci.recipes.size()) ci.recipes.set(index, r);
                else ci.recipes.add(r);
                plugin.items().changed();
                p.sendMessage(Ui.text("&a[ACI] Recipe saved."));
                new RecipeListMenu(plugin, ci).open(p);
            }
            default -> { }
        }
    }

    @Override
    public void onBottomClick(InventoryClickEvent e) {
        if (e.isShiftClick()) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            for (int slot : GRID) {
                if (inv.getItem(slot) == null || inv.getItem(slot).getType().isAir()) {
                    setGhost(slot, item);
                    return;
                }
            }
            return;
        }
        super.onBottomClick(e);
    }
}
