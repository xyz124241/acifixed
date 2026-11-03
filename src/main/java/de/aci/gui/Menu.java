package de.aci.gui;

import de.aci.AciPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Basisklasse fuer alle Menues. Klicks ins obere Inventar sind standardmaessig abgebrochen. */
public abstract class Menu implements InventoryHolder {
    protected final AciPlugin plugin;
    protected Inventory inv;

    protected Menu(AciPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** Klick ins Menue (Event ist schon abgebrochen). */
    public abstract void onClick(InventoryClickEvent e);

    /** Klick ins Spieler-Inventar unten. */
    public void onBottomClick(InventoryClickEvent e) {
        if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
    }

    public void onClose(InventoryCloseEvent e) {}
}
