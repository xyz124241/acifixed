package de.aci;

import org.bukkit.potion.PotionEffectType;

/** Ein dauerhafter Effekt, den ein Custom Item in einem bestimmten Slot gibt. */
public final class ItemEffect {
    public PotionEffectType type;
    /** 1 = Stufe I, 2 = Stufe II ... */
    public int level;
    public EquipSlot slot;
    /** 0 = nur solange getragen, sonst Effektdauer in Sekunden. */
    public int seconds;

    public ItemEffect(PotionEffectType type, int level, EquipSlot slot, int seconds) {
        this.type = type;
        this.level = Math.max(1, level);
        this.slot = slot;
        this.seconds = Math.max(0, seconds);
    }
}
