package de.aci;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gibt Spielern die Effekte ihrer Custom Items (alle 0,5 s geprueft).
 * seconds = 0  -> Effekt nur solange das Item im Slot ist (kurze Dauer, staendig erneuert, ohne HUD-Blinken)
 * seconds > 0  -> Effekt mit dieser Dauer, wird erneuert solange das Item im Slot ist
 */
public final class EffectTask extends BukkitRunnable {

    private static final int HOLD = 220;          // 11 s, damit das HUD-Icon nicht blinkt
    private static final int REFRESH_BELOW = 160; // erneuern, wenn weniger als 8 s uebrig

    private final AciPlugin plugin;
    private final Map<UUID, Set<PotionEffectType>> tracked = new HashMap<>();

    public EffectTask(AciPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tracked.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player p : Bukkit.getOnlinePlayers()) tick(p);
    }

    private void tick(Player p) {
        Map<PotionEffectType, ItemEffect> want = new HashMap<>();
        PlayerInventory inv = p.getInventory();
        consider(inv.getItemInMainHand(), EquipSlot.MAIN_HAND, want);
        consider(inv.getItemInOffHand(), EquipSlot.OFF_HAND, want);
        consider(inv.getHelmet(), EquipSlot.HELMET, want);
        consider(inv.getChestplate(), EquipSlot.CHESTPLATE, want);
        consider(inv.getLeggings(), EquipSlot.LEGGINGS, want);
        consider(inv.getBoots(), EquipSlot.BOOTS, want);
        for (ItemStack s : inv.getStorageContents()) consider(s, null, want);

        Set<PotionEffectType> constantNow = new HashSet<>();
        for (ItemEffect e : want.values()) {
            int amp = e.level - 1;
            PotionEffect cur = p.getPotionEffect(e.type);
            if (e.seconds <= 0) {
                constantNow.add(e.type);
                if (cur != null && cur.isInfinite() && cur.getAmplifier() >= amp) continue;
                if (cur == null || cur.getAmplifier() < amp
                        || (cur.getAmplifier() == amp && cur.getDuration() < REFRESH_BELOW)) {
                    p.addPotionEffect(new PotionEffect(e.type, HOLD, amp, true, false, true));
                }
            } else {
                int full = e.seconds * 20;
                if (cur != null && cur.isInfinite() && cur.getAmplifier() >= amp) continue;
                if (cur == null || cur.getAmplifier() < amp
                        || (cur.getAmplifier() == amp && cur.getDuration() < full - 100)) {
                    p.addPotionEffect(new PotionEffect(e.type, full, amp, true, false, true));
                }
            }
        }

        // Effekte entfernen, die wir selbst gegeben haben und die jetzt nicht mehr gelten
        Set<PotionEffectType> prev = tracked.getOrDefault(p.getUniqueId(), Set.of());
        for (PotionEffectType t : prev) {
            if (constantNow.contains(t)) continue;
            PotionEffect cur = p.getPotionEffect(t);
            if (cur != null && !cur.isInfinite() && cur.getDuration() <= HOLD + 10) p.removePotionEffect(t);
        }
        if (constantNow.isEmpty()) tracked.remove(p.getUniqueId());
        else tracked.put(p.getUniqueId(), constantNow);
    }

    /** slot == null: Item liegt nur im Inventar (zaehlt nur fuer ANYWHERE). */
    private void consider(ItemStack stack, EquipSlot slot, Map<PotionEffectType, ItemEffect> want) {
        CustomItem ci = plugin.items().get(stack);
        if (ci == null) return;
        for (ItemEffect e : ci.effects) {
            if (e.slot != EquipSlot.ANYWHERE && e.slot != slot) continue;
            ItemEffect old = want.get(e.type);
            if (old == null || e.level > old.level) want.put(e.type, e);
        }
    }
}
