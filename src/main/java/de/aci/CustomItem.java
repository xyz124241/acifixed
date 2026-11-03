package de.aci;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomItem {
    public final String id;
    /** Display name accepts MiniMessage gradients and legacy ampersand codes. */
    public String name;
    public Material material = Material.AMETHYST_SHARD;
    /** Texture identifier in the ACI resource pack; null keeps the vanilla model. */
    public String texture;
    public PotionType potionType;
    public final List<String> lore = new ArrayList<>();
    public final List<ItemEffect> effects = new ArrayList<>();
    public final List<CustomRecipe> recipes = new ArrayList<>();

    /** Automatic offhand ability settings. A cooldown of 0 triggers once when the item is equipped in the offhand. */
    public int cooldownSeconds = 0;
    public boolean autoOffhand = false;
    public final List<ItemEffect> abilityEffects = new ArrayList<>();

    /** Per-item attributes and enchantments. */
    public final List<ItemAttribute> attributes = new ArrayList<>();
    public final Map<String, Integer> enchantments = new LinkedHashMap<>();

    /** Custom shulker settings (only used with a shulker-box base material). */
    public int shulkerRows = 3;
    public boolean shulkerAutoPickup = true;

    public CustomItem(String id) {
        this.id = id;
        this.name = "&f" + id;
        this.texture = id;
    }
}
