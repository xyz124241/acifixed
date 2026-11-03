package de.aci.gui;

import de.aci.AciPlugin;
import de.aci.CustomItem;
import de.aci.EquipSlot;
import de.aci.ItemEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Effects of an item. Top: the item's current effects (click to change them);
 * bottom: all available effects to add.
 */
public final class EffectsMenu extends Menu {
    private static final int[] DURATIONS = {0, 30, 60, 300, 480, 900, 1800};
    private static final int MAX_EFFECTS = 18;
    private static final int PAGE_SIZE = 18;

    private final CustomItem ci;
    private final int page;
    private final int pages;
    private final List<PotionEffectType> palette = new ArrayList<>();

    public EffectsMenu(AciPlugin plugin, CustomItem ci, int page) {
        super(plugin);
        this.ci = ci;
        for (PotionEffectType t : Registry.EFFECT) palette.add(t);
        palette.sort(Comparator.comparing(t -> t.getKey().getKey()));
        this.pages = Math.max(1, (palette.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.min(Math.max(page, 0), pages - 1);
        inv = Bukkit.createInventory(this, 54, Ui.text("&8Effects: &0" + Ui.plain(ci.name)));

        for (int i = 0; i < 54; i++) inv.setItem(i, Ui.pane());
        for (int i = 0; i < MAX_EFFECTS; i++) inv.setItem(i, null);
        for (int i = 0; i < ci.effects.size() && i < MAX_EFFECTS; i++) inv.setItem(i, effectIcon(ci.effects.get(i)));

        inv.setItem(22, Ui.button(Material.PAPER, "&6How this works",
                "&7Top: the item's current effects.",
                "&eLeft click: &7level +1 (1-10)",
                "&eRight click: &7change slot",
                "&eShift-left: &7change duration",
                "&eShift-right: &7remove effect",
                "",
                "&7Bottom: click an effect to add it."));

        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = this.page * PAGE_SIZE + i;
            inv.setItem(27 + i, null);
            if (idx >= palette.size()) continue;
            inv.setItem(27 + i, paletteIcon(palette.get(idx)));
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, Ui.pane());
        inv.setItem(45, Ui.button(Material.ARROW, "&cBack to editor"));
        if (this.page > 0) inv.setItem(47, Ui.button(Material.ARROW, "&ePrevious page"));
        if (this.page < pages - 1) inv.setItem(51, Ui.button(Material.ARROW, "&eNext page"));
        inv.setItem(49, Ui.button(Material.BREWING_STAND, "&7Effects " + (this.page + 1) + "/" + pages));
    }

    private ItemStack effectIcon(ItemEffect e) {
        ItemStack s = new ItemStack(effectMaterial(e.type));
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.translatable(e.type.translationKey(), NamedTextColor.GREEN)
                .append(Component.text(" " + Ui.roman(e.level), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Ui.text("&7Active in: &f" + e.slot.label),
                Ui.text("&7Duration: &f" + (e.seconds <= 0 ? "while worn" : Ui.fmtSeconds(e.seconds) + " (refreshed)")),
                Ui.text(""),
                Ui.text("&eLeft click: &7level +1"),
                Ui.text("&eRight click: &7change slot"),
                Ui.text("&eShift-left: &7change duration"),
                Ui.text("&eShift-right: &7remove")));
        m.addItemFlags(ItemFlag.values());
        s.setItemMeta(m);
        return s;
    }

    private ItemStack paletteIcon(PotionEffectType t) {
        ItemStack s = new ItemStack(effectMaterial(t));
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.translatable(t.translationKey(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Ui.text("&7" + t.getKey().getKey()), Ui.text("&aClick: add")));
        m.addItemFlags(ItemFlag.values());
        s.setItemMeta(m);
        return s;
    }


    private Material effectMaterial(PotionEffectType t) {
        String key=t.getKey().getKey();
        return switch(key) {
            case "speed" -> Material.SUGAR;
            case "slowness" -> Material.COBWEB;
            case "haste" -> Material.GOLDEN_PICKAXE;
            case "mining_fatigue" -> Material.IRON_PICKAXE;
            case "strength" -> Material.BLAZE_POWDER;
            case "instant_health", "health_boost" -> Material.GOLDEN_APPLE;
            case "instant_damage" -> Material.FERMENTED_SPIDER_EYE;
            case "jump_boost" -> Material.RABBIT_FOOT;
            case "nausea" -> Material.POISONOUS_POTATO;
            case "regeneration" -> Material.GHAST_TEAR;
            case "resistance" -> Material.SHIELD;
            case "fire_resistance" -> Material.MAGMA_CREAM;
            case "water_breathing" -> Material.PUFFERFISH;
            case "invisibility" -> Material.GLASS;
            case "blindness", "darkness" -> Material.BLACK_DYE;
            case "night_vision" -> Material.GOLDEN_CARROT;
            case "hunger" -> Material.ROTTEN_FLESH;
            case "weakness" -> Material.FERMENTED_SPIDER_EYE;
            case "poison" -> Material.SPIDER_EYE;
            case "wither" -> Material.WITHER_ROSE;
            case "absorption" -> Material.ENCHANTED_GOLDEN_APPLE;
            case "saturation" -> Material.CAKE;
            case "glowing" -> Material.GLOWSTONE_DUST;
            case "levitation" -> Material.SHULKER_SHELL;
            case "luck" -> Material.EMERALD;
            case "slow_falling" -> Material.PHANTOM_MEMBRANE;
            case "dolphins_grace" -> Material.COD;
            case "conduit_power" -> Material.HEART_OF_THE_SEA;
            case "hero_of_the_village" -> Material.EMERALD_BLOCK;
            default -> Material.POTION;
        };
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slot < MAX_EFFECTS) {
            if (slot >= ci.effects.size()) return;
            ItemEffect eff = ci.effects.get(slot);
            ClickType c = e.getClick();
            if (c == ClickType.SHIFT_RIGHT) {
                ci.effects.remove(slot);
            } else if (c == ClickType.SHIFT_LEFT) {
                int idx = 0;
                for (int i = 0; i < DURATIONS.length; i++) if (DURATIONS[i] == eff.seconds) idx = i;
                eff.seconds = DURATIONS[(idx + 1) % DURATIONS.length];
            } else if (c == ClickType.RIGHT) {
                eff.slot = eff.slot.next();
            } else if (c == ClickType.LEFT) {
                eff.level = eff.level % 10 + 1;
            } else {
                return;
            }
            plugin.items().changed();
            new EffectsMenu(plugin, ci, page).open(p);
            return;
        }

        if (slot >= 27 && slot < 27 + PAGE_SIZE) {
            int idx = page * PAGE_SIZE + (slot - 27);
            if (idx >= palette.size()) return;
            if (ci.effects.size() >= MAX_EFFECTS) {
                p.sendMessage(Ui.text("&c[ACI] Maximum " + MAX_EFFECTS + " effects per item."));
                return;
            }
            ci.effects.add(new ItemEffect(palette.get(idx), 1, EquipSlot.OFF_HAND, 0));
            plugin.items().changed();
            new EffectsMenu(plugin, ci, page).open(p);
            return;
        }

        if (slot == 45) new ItemEditorMenu(plugin, ci).open(p);
        else if (slot == 47 && page > 0) new EffectsMenu(plugin, ci, page - 1).open(p);
        else if (slot == 51 && page < pages - 1) new EffectsMenu(plugin, ci, page + 1).open(p);
    }
}
