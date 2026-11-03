package de.aci;

import de.aci.gui.Ui;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Asks a player for a text input in chat (used by menus: name, id, lore, item lookup ...). */
public final class ChatPrompt implements Listener {

    private record Pending(Consumer<String> ok, Runnable cancel) {}

    private final AciPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(AciPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String message, Consumer<String> ok, Runnable cancel) {
        pending.put(p.getUniqueId(), new Pending(ok, cancel));
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        p.sendMessage(Ui.text("&6[ACI] &f" + message + " &7(or type &ccancel&7)"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) pd.cancel().run();
            else pd.ok().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
