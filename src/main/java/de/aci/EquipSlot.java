package de.aci;

import org.bukkit.Material;

/** Slot in which a custom item grants its effect. */
public enum EquipSlot {
    MAIN_HAND("Main hand", Material.IRON_SWORD),
    OFF_HAND("Off hand", Material.SHIELD),
    HELMET("Helmet", Material.IRON_HELMET),
    CHESTPLATE("Chestplate", Material.IRON_CHESTPLATE),
    LEGGINGS("Leggings", Material.IRON_LEGGINGS),
    BOOTS("Boots", Material.IRON_BOOTS),
    ANYWHERE("Inventory", Material.CHEST);

    public final String label;
    public final Material icon;

    EquipSlot(String label, Material icon) {
        this.label = label;
        this.icon = icon;
    }

    public EquipSlot next() {
        EquipSlot[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
