package de.aci;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies configured abilities while a custom item is in the offhand. */
public final class AbilityTask extends BukkitRunnable {
    private final AciPlugin plugin;
    private final Map<UUID, Map<String, Long>> nextUse = new HashMap<>();
    private final Map<UUID, String> lastOffhandItem = new HashMap<>();

    public AbilityTask(AciPlugin plugin) { this.plugin = plugin; }

    @Override public void run() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack stack = player.getInventory().getItemInOffHand();
            CustomItem item = plugin.items().get(stack);
            if (item == null || !item.autoOffhand || item.abilityEffects.isEmpty()) {
                lastOffhandItem.remove(player.getUniqueId());
                continue;
            }
            String previousId = lastOffhandItem.put(player.getUniqueId(), item.id);

            // With zero cooldown, trigger once when the item is equipped in the offhand.
            if (item.cooldownSeconds == 0) {
                if (item.id.equals(previousId)) continue;
            } else {
                Map<String, Long> playerCooldowns = nextUse.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
                if (now < playerCooldowns.getOrDefault(item.id, 0L)) continue;
            }

            for (ItemEffect effect : item.abilityEffects) {
                int duration = Math.max(1, effect.seconds) * 20;
                player.addPotionEffect(new PotionEffect(effect.type, duration, effect.level - 1, true, true, true));
            }

            if (item.cooldownSeconds > 0) {
                // Classic item cooldown indicator. The internal timer is still per custom item.
                player.setCooldown(item.material, item.cooldownSeconds * 20);
                nextUse.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                        .put(item.id, now + item.cooldownSeconds * 1000L);
            }
        }
        nextUse.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        lastOffhandItem.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
