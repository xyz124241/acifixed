package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomRecipe;
import de.aci.ItemManager.RecipeRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** One page of the recipe book: exactly one recipe. Arrows to flip pages, click an ingredient to jump to its recipe. */
public final class RecipeBookMenu extends Menu {
    private static final int[] GRID = RecipeEditorMenu.GRID;
    private static final int ARROW = 23, RESULT = 25, PREV = 45, INDEX = 49, NEXT = 53;

    private final int pos;
    private final List<RecipeRef> refs;

    public RecipeBookMenu(AciPlugin plugin, int pos) {
        super(plugin);
        this.refs = plugin.items().refs();
        this.pos = Math.min(Math.max(pos, 0), Math.max(0, refs.size() - 1));

        if (refs.isEmpty()) {
            inv = Bukkit.createInventory(this, 54, Ui.text("&8Recipe book"));
            for (int i = 0; i < 54; i++) inv.setItem(i, Ui.pane());
            inv.setItem(INDEX, Ui.button(Material.BOOK, "&6Back to overview"));
            return;
        }

        RecipeRef ref = refs.get(this.pos);
        CustomRecipe r = ref.item().recipes.get(ref.index());
        Component title = Ui.text("&8Recipe " + (this.pos + 1) + "/" + refs.size() + " &8- ")
                .append(Ui.text(ref.item().name));
        inv = Bukkit.createInventory(this, 54, title);
        for (int i = 0; i < 54; i++) inv.setItem(i, Ui.pane());

        for (int i = 0; i < 9; i++) {
            String token = r.grid[i];
            if (token == null) {
                inv.setItem(GRID[i], null);
                continue;
            }
            ItemStack st = plugin.items().stackForToken(token, 1);
            if (token.startsWith("aci:") && hasRecipe(token.substring(4))) {
                Ui.addLore(st, "", "&eClick: &7view that ingredient's recipe");
            }
            inv.setItem(GRID[i], st);
        }
        inv.setItem(ARROW, Ui.button(Material.ARROW, "&7yields"));
        inv.setItem(RESULT, plugin.items().create(ref.item(), r.amount));

        inv.setItem(INDEX, Ui.button(Material.BOOK, "&6Overview"));
        if (this.pos > 0) inv.setItem(PREV, Ui.button(Material.ARROW, "&ePrevious recipe"));
        if (this.pos < refs.size() - 1) inv.setItem(NEXT, Ui.button(Material.ARROW, "&eNext recipe"));
    }

    private boolean hasRecipe(String itemId) {
        for (RecipeRef r : refs) if (r.item().id.equals(itemId)) return true;
        return false;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slot == INDEX) {
            new RecipeIndexMenu(plugin, pos / 45).open(p);
        } else if (slot == PREV && pos > 0 && !refs.isEmpty()) {
            new RecipeBookMenu(plugin, pos - 1).open(p);
        } else if (slot == NEXT && pos < refs.size() - 1) {
            new RecipeBookMenu(plugin, pos + 1).open(p);
        } else if (!refs.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (GRID[i] != slot) continue;
                RecipeRef ref = refs.get(pos);
                String token = ref.item().recipes.get(ref.index()).grid[i];
                if (token == null || !token.startsWith("aci:")) return;
                String id = token.substring(4);
                for (int k = 0; k < refs.size(); k++) {
                    if (refs.get(k).item().id.equals(id)) {
                        new RecipeBookMenu(plugin, k).open(p);
                        return;
                    }
                }
            }
        }
    }
}
