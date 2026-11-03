package de.aci;

import de.aci.gui.Ui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Verwaltet alle Custom Items, speichert sie in items.yml und registriert die Rezepte. */
public final class ItemManager implements Listener {

    public record RecipeRef(CustomItem item, int index) {}

    private final AciPlugin plugin;
    private final File file;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();
    private final NamespacedKey idKey;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public ItemManager(AciPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "items.yml");
        this.idKey = new NamespacedKey(plugin, "id");
    }

    // ------------------------------------------------------------------ Zugriff

    public Collection<CustomItem> all() {
        return items.values();
    }

    public CustomItem get(String id) {
        return id == null ? null : items.get(id);
    }

    public CustomItem get(ItemStack stack) {
        return get(idOf(stack));
    }

    public String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public CustomItem createItem(String id) {
        CustomItem ci = new CustomItem(id);
        List<String> tex = plugin.pack().textureNames();
        ci.texture = tex.contains(id) ? id : (tex.isEmpty() ? id : tex.get(0));
        items.put(id, ci);
        changed();
        return ci;
    }

    public void remove(CustomItem ci) {
        items.remove(ci.id);
        changed();
    }

    /** Alle Rezepte aller Items in fester Reihenfolge (fuer das /ci Rezeptbuch). */
    public List<RecipeRef> refs() {
        List<RecipeRef> out = new ArrayList<>();
        for (CustomItem ci : items.values())
            for (int i = 0; i < ci.recipes.size(); i++) out.add(new RecipeRef(ci, i));
        return out;
    }

    // ------------------------------------------------------------------ ItemStacks

    public ItemStack create(CustomItem ci, int amount) {
        ItemStack st = new ItemStack(ci.material, Math.max(1, amount));
        ItemMeta m = st.getItemMeta();
        m.displayName(Ui.text(ci.name));
        List<Component> lore = new ArrayList<>();
        for (String l : ci.lore) lore.add(Ui.text("&7" + l));
        for (ItemEffect e : ci.effects) lore.add(Ui.effectLine(e));
        if (!lore.isEmpty()) m.lore(lore);
        // Only force a custom texture when one is actually set; otherwise the item keeps its
        // own vanilla look (important for potions and plain items such as iron ingots etc.).
        if (ci.texture != null && !ci.texture.isBlank()) {
            NamespacedKey itemModel = NamespacedKey.fromString(PackManager.NAMESPACE + ":" + ci.texture);
            if (itemModel != null && plugin.pack().textureNames().contains(ci.texture)) {
                m.setItemModel(itemModel);
            }
        }
        // Apply configurable item attributes.
        for (int i = 0; i < ci.attributes.size(); i++) {
            ItemAttribute a = ci.attributes.get(i);
            try {
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(a.attribute.toLowerCase(Locale.ROOT)));
                if (attribute == null) throw new IllegalArgumentException("Unknown attribute: " + a.attribute);
                AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(a.operation.toUpperCase(Locale.ROOT));
                EquipmentSlotGroup group = EquipmentSlotGroup.getByName(a.slotGroup.toLowerCase(Locale.ROOT).replace("_", ""));
                if (group == null) throw new IllegalArgumentException("Unknown equipment slot group: " + a.slotGroup);
                m.addAttributeModifier(attribute, new AttributeModifier(
                        new NamespacedKey(plugin, "aci_" + ci.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_") + "_attr_" + i), a.amount, operation, group));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid attribute on " + ci.id + ": " + a.attribute + " (" + ex.getMessage() + ")");
            }
        }
        // Unsafe enchantments are intentional: custom items may use any enchantment.
        for (Map.Entry<String, Integer> entry : ci.enchantments.entrySet()) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey().toLowerCase(Locale.ROOT)));
            if (enchantment != null) m.addEnchant(enchantment, Math.max(1, entry.getValue()), true);
        }
        if (isShulkerMaterial(ci.material)) {
            m.getPersistentDataContainer().set(new NamespacedKey(plugin, "shulker_rows"), PersistentDataType.INTEGER, Math.max(1, Math.min(6, ci.shulkerRows)));
            m.getPersistentDataContainer().set(new NamespacedKey(plugin, "shulker_auto_pickup"), PersistentDataType.BYTE, (byte)(ci.shulkerAutoPickup ? 1 : 0));
        }
        if (isPotionMaterial(ci.material) && m instanceof PotionMeta pm && ci.potionType != null) {
            pm.setBasePotionType(ci.potionType);
        }
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, ci.id);
        st.setItemMeta(m);
        return st;
    }

    public static boolean isPotionMaterial(Material m) {
        return m == Material.POTION || m == Material.SPLASH_POTION
                || m == Material.LINGERING_POTION || m == Material.TIPPED_ARROW;
    }

    public static boolean isShulkerMaterial(Material m) {
        return m != null && m.name().endsWith("SHULKER_BOX");
    }

    public void give(Player p, CustomItem ci, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, ci.material.getMaxStackSize());
            left -= n;
            Map<Integer, ItemStack> rest = p.getInventory().addItem(create(ci, n));
            rest.values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        }
    }

    /**
     * Token for a recipe cell: "aci:id", "mc:MATERIAL", "mc:MATERIAL:potion_type"
     * (only for potion-like materials, keeps the exact potion/effect), or null.
     */
    public String tokenFor(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        String id = idOf(stack);
        if (id != null && items.containsKey(id)) return "aci:" + id;
        Material mat = stack.getType();
        if (isPotionMaterial(mat) && stack.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null) {
            return "mc:" + mat.name() + ":" + pm.getBasePotionType().getKey().getKey();
        }
        return "mc:" + mat.name();
    }

    public ItemStack stackForToken(String token, int amount) {
        if (token == null) return new ItemStack(Material.AIR);
        if (token.startsWith("aci:")) {
            CustomItem ci = items.get(token.substring(4));
            if (ci != null) return create(ci, amount);
            return Ui.button(Material.BARRIER, "&cMissing: " + token.substring(4));
        }
        String[] parts = token.substring(3).split(":", 2);
        Material m = Material.matchMaterial(parts[0]);
        if (m == null) return new ItemStack(Material.BARRIER, amount);
        ItemStack st = new ItemStack(m, amount);
        if (parts.length > 1 && isPotionMaterial(m)) {
            PotionType pt = potionTypeByName(parts[1]);
            if (pt != null && st.getItemMeta() instanceof PotionMeta pm) {
                pm.setBasePotionType(pt);
                st.setItemMeta(pm);
            }
        }
        return st;
    }

    /**
     * Parses free text typed by a player (in the "pick by name" chat prompt) into a recipe token.
     * Accepts an existing custom item id ("aci:foo" or just "foo"), a vanilla material name
     * ("iron_ingot", "minecraft:iron_ingot"), or a potion/splash_potion/lingering_potion/tipped_arrow
     * with a potion type ("potion:fire_resistance", "splash_potion:strength"). Returns null if
     * nothing matches.
     */
    public String tokenFromName(String raw) {
        if (raw == null) return null;
        String input = raw.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return null;
        if (input.startsWith("aci:")) input = input.substring(4);
        if (items.containsKey(input)) return "aci:" + input;

        String matPart = input;
        String potionPart = null;
        int colon = input.indexOf(':');
        if (colon > 0 && !input.startsWith("minecraft:")) {
            matPart = input.substring(0, colon);
            potionPart = input.substring(colon + 1);
        }
        Material mat = Material.matchMaterial(matPart);
        if (mat == null || !mat.isItem()) return null;
        if (potionPart == null || potionPart.isBlank()) return "mc:" + mat.name();
        if (!isPotionMaterial(mat)) return null;
        PotionType pt = potionTypeByName(potionPart);
        return pt == null ? null : "mc:" + mat.name() + ":" + pt.getKey().getKey();
    }

    private static PotionType potionTypeByName(String name) {
        String key = name.toLowerCase(Locale.ROOT).trim();
        if (key.startsWith("minecraft:")) key = key.substring(10);
        return Registry.POTION.get(NamespacedKey.minecraft(key));
    }

    // ------------------------------------------------------------------ Laden / Speichern

    /** One-time safe upgrade: replace the old built-in crystal set with the 38 new definitions. */
    public void migrateCrystalDefaults() {
        File marker = new File(plugin.getDataFolder(), ".aci-38-crystals-1_4_0-migrated");
        if (marker.exists()) return;
        try {
            if (file.isFile()) {
                File backup = new File(plugin.getDataFolder(), "items.yml.before-38-crystals-1.4.0.bak");
                if (!backup.exists()) java.nio.file.Files.copy(file.toPath(), backup.toPath());
            }
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration defaults;
            try (var in = plugin.getResource("items.yml")) {
                if (in == null) throw new IOException("embedded items.yml missing");
                defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
            ConfigurationSection defItems = defaults.getConfigurationSection("items");
            if (defItems == null || defItems.getKeys(false).size() != 38) throw new IOException("expected exactly 38 embedded crystal items");
            java.util.Set<String> newCrystalIds = new java.util.HashSet<>(defItems.getKeys(false));
            for (String id : defItems.getKeys(false)) {
                ConfigurationSection child = defItems.getConfigurationSection(id);
                if (child != null) current.set("items." + id, child.getValues(false));
            }
            // Remove obsolete default/custom crystal entries which are not part of the requested
            // 38-crystal collection. Non-crystal custom items are deliberately preserved.
            ConfigurationSection currentItems = current.getConfigurationSection("items");
            if (currentItems != null) {
                for (String id : new java.util.ArrayList<>(currentItems.getKeys(false))) {
                    if (id.toLowerCase(Locale.ROOT).contains("crystal") && !newCrystalIds.contains(id)) {
                        current.set("items." + id, null);
                    }
                }
            }
            current.save(file);
            File[] stale = new File(plugin.getDataFolder(), "textures").listFiles((d,n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (stale != null) for (File f : stale) {
                String id = f.getName().substring(0, f.getName().length()-4).toLowerCase(Locale.ROOT);
                if (id.contains("crystal") && !newCrystalIds.contains(id)) java.nio.file.Files.deleteIfExists(f.toPath());
            }
            if (!marker.createNewFile() && !marker.exists()) throw new IOException("migration marker could not be created");
            plugin.getLogger().info("ACI: old default crystals replaced with exactly 38 new crystals; obsolete crystal IDs and textures removed; items.yml backup created.");
        } catch (Exception ex) { plugin.getLogger().severe("ACI crystal migration failed safely: " + ex.getMessage()); }
    }

    public void load() {
        items.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            CustomItem ci = new CustomItem(id);
            ci.name = s.getString("name", "&f" + id);
            Material mat = Material.matchMaterial(s.getString("material", "AMETHYST_SHARD"));
            ci.material = mat != null && mat.isItem() ? mat : Material.AMETHYST_SHARD;
            String tex = s.getString("texture", id);
            ci.texture = (tex == null || tex.isBlank()) ? null : tex;
            String pt = s.getString("potion-type", "");
            ci.potionType = pt.isBlank() ? null : potionTypeByName(pt);
            ci.lore.addAll(s.getStringList("lore"));
            ci.cooldownSeconds = Math.max(0, Math.min(3600, s.getInt("cooldown-seconds", 0)));
            ci.autoOffhand = s.getBoolean("auto-offhand", false);
            ci.shulkerRows = Math.max(1, Math.min(6, s.getInt("shulker-rows", 3)));
            ci.shulkerAutoPickup = s.getBoolean("shulker-auto-pickup", true);
            for (Map<?, ?> a : s.getMapList("attributes")) {
                ci.attributes.add(new ItemAttribute(String.valueOf(a.get("attribute")),
                        a.get("amount") instanceof Number n ? n.doubleValue() : 0.0,
                        String.valueOf(a.containsKey("operation") ? a.get("operation") : "ADD_NUMBER"),
                        String.valueOf(a.containsKey("slot") ? a.get("slot") : "ANY")));
            }
            ConfigurationSection ench = s.getConfigurationSection("enchants");
            if (ench != null) for (String key : ench.getKeys(false)) ci.enchantments.put(key, Math.max(1, ench.getInt(key, 1)));

            for (Map<?, ?> m : s.getMapList("effects")) {
                PotionEffectType type = effectType(String.valueOf(m.get("type")));
                if (type == null) {
                    plugin.getLogger().warning("Item " + id + ": unknown effect '" + m.get("type") + "'");
                    continue;
                }
                EquipSlot slot;
                try {
                    slot = EquipSlot.valueOf(String.valueOf(m.get("slot")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    slot = EquipSlot.OFF_HAND;
                }
                ci.effects.add(new ItemEffect(type, num(m.get("level"), 1), slot, num(m.get("seconds"), 0)));
            }
            for (Map<?, ?> m : s.getMapList("ability-effects")) {
                PotionEffectType type = effectType(String.valueOf(m.get("type")));
                if (type != null) ci.abilityEffects.add(new ItemEffect(type, num(m.get("level"), 1), EquipSlot.OFF_HAND, num(m.get("seconds"), 10)));
            }

            for (Map<?, ?> m : s.getMapList("recipes")) {
                CustomRecipe r = new CustomRecipe();
                r.amount = Math.max(1, num(m.get("amount"), 1));
                if (m.get("grid") instanceof List<?> g) {
                    for (int i = 0; i < 9 && i < g.size(); i++) {
                        String t = g.get(i) == null ? "" : String.valueOf(g.get(i));
                        r.grid[i] = t.isEmpty() ? null : t;
                    }
                }
                if (!r.isEmpty()) ci.recipes.add(r);
            }
            items.put(id, ci);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (CustomItem ci : items.values()) {
            String p = "items." + ci.id + ".";
            y.set(p + "name", ci.name);
            y.set(p + "material", ci.material.name());
            y.set(p + "texture", ci.texture == null ? "" : ci.texture);
            y.set(p + "potion-type", ci.potionType == null ? "" : ci.potionType.getKey().getKey());
            y.set(p + "lore", ci.lore);
            y.set(p + "cooldown-seconds", ci.cooldownSeconds);
            y.set(p + "auto-offhand", ci.autoOffhand);
            y.set(p + "shulker-rows", ci.shulkerRows);
            y.set(p + "shulker-auto-pickup", ci.shulkerAutoPickup);
            List<Map<String, Object>> attr = new ArrayList<>();
            for (ItemAttribute a : ci.attributes) {
                Map<String,Object> m = new LinkedHashMap<>(); m.put("attribute",a.attribute); m.put("amount",a.amount);
                m.put("operation",a.operation); m.put("slot",a.slotGroup); attr.add(m);
            }
            y.set(p + "attributes", attr);
            y.set(p + "enchants", ci.enchantments);
            List<Map<String, Object>> ability = new ArrayList<>();
            for (ItemEffect e : ci.abilityEffects) {
                Map<String,Object> m = new LinkedHashMap<>(); m.put("type",e.type.getKey().getKey()); m.put("level",e.level); m.put("seconds",e.seconds); ability.add(m);
            }
            y.set(p + "ability-effects", ability);

            List<Map<String, Object>> eff = new ArrayList<>();
            for (ItemEffect e : ci.effects) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", e.type.getKey().getKey());
                m.put("level", e.level);
                m.put("slot", e.slot.name());
                m.put("seconds", e.seconds);
                eff.add(m);
            }
            y.set(p + "effects", eff);

            List<Map<String, Object>> rec = new ArrayList<>();
            for (CustomRecipe r : ci.recipes) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("amount", r.amount);
                List<String> g = new ArrayList<>();
                for (String t : r.grid) g.add(t == null ? "" : t);
                m.put("grid", g);
                rec.add(m);
            }
            y.set(p + "recipes", rec);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("items.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static PotionEffectType effectType(String name) {
        String key = name.toLowerCase(Locale.ROOT).trim();
        if (key.startsWith("minecraft:")) key = key.substring(10);
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    /** Nach jeder Aenderung: speichern, Rezepte neu registrieren, Items bei Spielern aktualisieren. */
    public void changed() {
        save();
        registerRecipes();
        if (plugin.pack() != null) plugin.pack().rebuild();
        Bukkit.getOnlinePlayers().forEach(this::refreshInventory);
    }

    // ------------------------------------------------------------------ Rezepte

    public void registerRecipes() {
        for (NamespacedKey k : registered) Bukkit.removeRecipe(k);
        registered.clear();

        for (CustomItem ci : items.values()) {
            for (int idx = 0; idx < ci.recipes.size(); idx++) {
                CustomRecipe r = ci.recipes.get(idx);
                try {
                    NamespacedKey key = new NamespacedKey(plugin, ci.id + "_" + idx);
                    ShapedRecipe sr = buildShaped(key, ci, r);
                    if (sr != null && Bukkit.addRecipe(sr)) registered.add(key);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Recipe " + ci.id + "#" + idx + " is invalid: " + ex.getMessage());
                }
            }
        }
    }

    private ShapedRecipe buildShaped(NamespacedKey key, CustomItem ci, CustomRecipe r) {
        // Auf den belegten Bereich zuschneiden
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            if (r.grid[i] == null) continue;
            minR = Math.min(minR, i / 3);
            maxR = Math.max(maxR, i / 3);
            minC = Math.min(minC, i % 3);
            maxC = Math.max(maxC, i % 3);
        }
        if (maxR < 0) return null;

        ShapedRecipe sr = new ShapedRecipe(key, create(ci, r.amount));
        String[] rows = new String[maxR - minR + 1];
        char next = 'A';
        Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
        for (int row = minR; row <= maxR; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = minC; col <= maxC; col++) {
                String t = r.grid[row * 3 + col];
                if (t == null) {
                    sb.append(' ');
                    continue;
                }
                char c = next++;
                sb.append(c);
                choices.put(c, choiceFor(t));
            }
            rows[row - minR] = sb.toString();
        }
        sr.shape(rows);
        choices.forEach(sr::setIngredient);
        return sr;
    }

    private RecipeChoice choiceFor(String token) {
        if (token.startsWith("aci:")) {
            CustomItem ci = items.get(token.substring(4));
            if (ci == null) throw new IllegalStateException("Item '" + token.substring(4) + "' does not exist");
            // Match custom items by their stable ACI id, not by all metadata.
            // This keeps renamed, dyed and used custom shulkers craftable.
            return new CustomItemChoice(ci.id);
        }
        String[] parts = token.substring(3).split(":", 2);
        Material m = Material.matchMaterial(parts[0]);
        if (m == null) throw new IllegalStateException("Material '" + parts[0] + "' does not exist");
        if (parts.length > 1 && isPotionMaterial(m)) {
            PotionType pt = potionTypeByName(parts[1]);
            if (pt == null) throw new IllegalStateException("Potion type '" + parts[1] + "' does not exist");
            return new PotionChoice(m, pt);
        }
        return new RecipeChoice.MaterialChoice(m);
    }


    /** Matches an ACI item by its persistent id while ignoring mutable metadata
     * such as shulker contents, custom names, lore and dye color. */
    private final class CustomItemChoice implements RecipeChoice {
        private final String itemId;

        private CustomItemChoice(String itemId) {
            this.itemId = itemId;
        }

        @Override
        public boolean test(ItemStack item) {
            return item != null && itemId.equals(idOf(item));
        }

        @Override
        public ItemStack getItemStack() {
            CustomItem ci = items.get(itemId);
            return ci == null ? new ItemStack(Material.AIR) : create(ci, 1);
        }

        @Override
        public RecipeChoice clone() {
            return new CustomItemChoice(itemId);
        }
    }

    /** Matches a potion-like ingredient by material + exact potion type, ignoring name/lore/color overrides. */
    private static final class PotionChoice implements RecipeChoice {
        private final Material material;
        private final PotionType potionType;

        PotionChoice(Material material, PotionType potionType) {
            this.material = material;
            this.potionType = potionType;
        }

        @Override
        public boolean test(ItemStack item) {
            if (item == null || item.getType() != material) return false;
            if (!(item.getItemMeta() instanceof PotionMeta pm)) return false;
            return pm.getBasePotionType() == potionType;
        }

        @Override
        public ItemStack getItemStack() {
            ItemStack st = new ItemStack(material);
            if (st.getItemMeta() instanceof PotionMeta pm) {
                pm.setBasePotionType(potionType);
                st.setItemMeta(pm);
            }
            return st;
        }

        @Override
        public RecipeChoice clone() {
            return new PotionChoice(material, potionType);
        }
    }

    // Custom items are intentionally allowed as ingredients in vanilla and custom recipes.

    // ------------------------------------------------------------------ Aktualisieren

    /** Ersetzt veraltete Custom Items im Inventar durch die aktuelle Version (Name, Lore, Textur). */
    public void refreshInventory(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            String id = idOf(s);
            if (id == null) continue;
            CustomItem ci = items.get(id);
            if (ci == null) {
                if (id.toLowerCase(Locale.ROOT).contains("crystal")) inv.setItem(i, null);
                continue;
            }
            ItemStack fresh = create(ci, s.getAmount());
            // Keep a dyed shulker's material color, custom name and virtual contents
            // when ACI refreshes player inventories after a join or configuration edit.
            if (isShulkerMaterial(ci.material) && isShulkerMaterial(s.getType())
                    && s.hasItemMeta() && fresh.hasItemMeta()) {
                ItemStack colored = new ItemStack(s.getType(), s.getAmount());
                var freshMeta = fresh.getItemMeta();
                colored.setItemMeta(freshMeta);
                fresh = colored;

                var sourceMeta = s.getItemMeta();
                var from = sourceMeta.getPersistentDataContainer();
                var freshMeta2 = fresh.getItemMeta();
                var to = freshMeta2.getPersistentDataContainer();
                NamespacedKey rowsKey = new NamespacedKey(plugin, "shulker_rows");
                NamespacedKey pickupKey = new NamespacedKey(plugin, "shulker_auto_pickup");
                NamespacedKey contentsKey = new NamespacedKey(plugin, "shulker_contents");
                NamespacedKey markerKey = new NamespacedKey(plugin, "aci_shulker");
                Integer rows = from.get(rowsKey, PersistentDataType.INTEGER);
                Byte pickup = from.get(pickupKey, PersistentDataType.BYTE);
                byte[] stored = from.get(contentsKey, PersistentDataType.BYTE_ARRAY);
                Byte marker = from.get(markerKey, PersistentDataType.BYTE);
                if (rows != null) to.set(rowsKey, PersistentDataType.INTEGER, rows);
                if (pickup != null) to.set(pickupKey, PersistentDataType.BYTE, pickup);
                if (stored != null) to.set(contentsKey, PersistentDataType.BYTE_ARRAY, stored);
                if (marker != null) to.set(markerKey, PersistentDataType.BYTE, marker);
                if (sourceMeta.displayName() != null) freshMeta2.displayName(sourceMeta.displayName());
                if (sourceMeta.lore() != null) freshMeta2.lore(new ArrayList<>(sourceMeta.lore()));
                fresh.setItemMeta(freshMeta2);
            }
            if (fresh.getType() != s.getType() || !fresh.isSimilar(s)) inv.setItem(i, fresh);
        }
    }
}
