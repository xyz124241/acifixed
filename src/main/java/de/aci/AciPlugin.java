package de.aci;

import de.aci.gui.AdminMainMenu;
import de.aci.gui.GuiListener;
import de.aci.gui.RecipeIndexMenu;
import de.aci.gui.Ui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AciPlugin extends JavaPlugin implements Listener, TabExecutor {

    private ItemManager items;
    private PackManager pack;
    private ChatPrompt prompts;
    private ShulkerManager shulkers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Existing installations keep their old config.yml; fill the configured server domain
        // when no custom GitHub release URL/public address has been entered yet.
        if (getConfig().getString("resource-pack.url", "").isBlank()
                && getConfig().getString("resource-pack.host.public-address", "").isBlank()) {
            getConfig().set("resource-pack.host.public-address", "neptunsmp.de");
            saveConfig();
        }
        if (!new File(getDataFolder(), "items.yml").exists()) saveResource("items.yml", false);
        new File(getDataFolder(), "textures").mkdirs();

        pack = new PackManager(this);
        items = new ItemManager(this);
        items.migrateCrystalDefaults();
        items.load();
        pack.rebuild();
        pack.start();
        items.registerRecipes();

        prompts = new ChatPrompt(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(items, this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(new GuiListener(), this);
        shulkers = new ShulkerManager(this);
        pm.registerEvents(shulkers, this);

        getCommand("aci").setExecutor(this);
        getCommand("aci").setTabCompleter(this);
        getCommand("ci").setExecutor(this);

        new EffectTask(this).runTaskTimer(this, 20L, 10L);
        new AbilityTask(this).runTaskTimer(this, 20L, 5L);
    }

    @Override
    public void onDisable() {
        if (shulkers != null) shulkers.saveAndCloseOpenInventories();
        if (pack != null) pack.stop();
    }

    public ItemManager items() { return items; }
    public PackManager pack() { return pack; }
    public ChatPrompt prompts() { return prompts; }

    /** config.yml, items.yml und Texturen neu einlesen, Pack neu bauen und an alle schicken. */
    public void reloadAll() {
        reloadConfig();
        items.migrateCrystalDefaults();
        items.load();
        pack.rebuild();
        pack.start();
        items.registerRecipes();
        Bukkit.getOnlinePlayers().forEach(p -> {
            items.refreshInventory(p);
            if (getConfig().getBoolean("resource-pack.send-on-join", true)) pack.sendTo(p);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        items.refreshInventory(p);
        if (getConfig().getBoolean("resource-pack.send-on-join", true)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) pack.sendTo(p);
            }, 20L);
        }
    }

    // ------------------------------------------------------------------ Befehle

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ci")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Only available in-game.");
                return true;
            }
            new RecipeIndexMenu(this, 0).open(p);
            return true;
        }

        // /aci
        if (args.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Only available in-game. Console: /aci reload | give <id> <player> [amount] | list");
                return true;
            }
            new AdminMainMenu(this, 0).open(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadAll();
                s.sendMessage(Ui.text("&a[ACI] Reloaded (" + items.all().size() + " items, "
                        + pack.textureNames().size() + " textures)."));
            }
            case "pack" -> {
                Bukkit.getOnlinePlayers().forEach(pack::sendTo);
                s.sendMessage(Ui.text("&a[ACI] Resource pack sent to all players."));
            }
            case "list" -> {
                s.sendMessage(Ui.text("&6[ACI] Items: &f" + String.join(", ",
                        items.all().stream().map(c -> c.id).toList())));
            }
            case "give" -> {
                if (args.length < 2) {
                    s.sendMessage(Ui.text("&c/aci give <id> [player] [amount]"));
                    return true;
                }
                CustomItem ci = items.get(args[1].toLowerCase(Locale.ROOT));
                if (ci == null) {
                    s.sendMessage(Ui.text("&cUnknown item: " + args[1]));
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player pl ? pl : null);
                if (target == null) {
                    s.sendMessage(Ui.text("&cPlayer not found."));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(2304, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        s.sendMessage(Ui.text("&cInvalid amount."));
                        return true;
                    }
                }
                items.give(target, ci, amount);
                s.sendMessage(Ui.text("&a[ACI] Gave " + amount + "x " + Ui.plain(ci.name) + " to " + target.getName() + "."));
            }
            default -> s.sendMessage(Ui.text("&c/aci [reload|pack|give|list]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("aci")) return out;
        if (args.length == 1) {
            for (String o : List.of("reload", "pack", "give", "list")) if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (CustomItem c : items.all()) if (c.id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(c.id);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
