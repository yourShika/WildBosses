package com.yourshika.wildbosses.command;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.army.ArmyEncounter;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.gui.BestiaryMenu;
import com.yourshika.wildbosses.gui.MainMenu;
import com.yourshika.wildbosses.integration.Updater;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles {@code /wildbosses} (alias {@code /wb}).
 */
public final class WildBossesCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("spawn", "army", "list", "active", "info", "gui", "killall", "reload", "restore", "update", "help");

    private final WildBossesPlugin plugin;

    public WildBossesCommand(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "restore" -> restore(sender);
            case "spawn" -> spawn(sender, args);
            case "army" -> army(sender, args);
            case "list" -> list(sender);
            case "active" -> active(sender);
            case "info" -> info(sender, args);
            case "gui" -> gui(sender);
            case "update" -> update(sender);
            case "killall" -> killAll(sender);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        if (denied(sender, "wildbosses.admin")) {
            return;
        }
        try {
            int count = plugin.reloadAll();
            plugin.messages().send(sender, "reloaded", Text.num("count", count));
        } catch (RuntimeException ex) {
            plugin.messages().send(sender, "reload-failed", Text.unparsed("error", String.valueOf(ex.getMessage())));
            plugin.getLogger().severe("Reload failed: " + ex);
        }
    }

    private void restore(CommandSender sender) {
        if (denied(sender, "wildbosses.admin")) {
            return;
        }
        int n = plugin.registry().restoreDefaults();   // factory-reset every bundled boss file
        // Only (re)build config.yml if it's actually missing - never wipe existing settings.
        if (!new java.io.File(plugin.getDataFolder(), "config.yml").exists()) {
            try {
                plugin.saveResource("config.yml", false);
            } catch (IllegalArgumentException ignored) {
                // no bundled config.yml (shouldn't happen)
            }
        }
        int loaded = plugin.reloadAll();
        sender.sendMessage(Text.mm("<green>Factory-reset <yellow>" + n + "<green> boss files "
                + "(config kept), reloaded <yellow>" + loaded + "<green> bosses."));
    }

    private BossDefinition randomBoss(boolean armyOnly) {
        List<BossDefinition> pool = new ArrayList<>();
        for (BossDefinition d : plugin.registry().all()) {
            if (armyOnly == d.isArmy()) {
                pool.add(d);
            }
        }
        if (pool.isEmpty()) {
            pool = new ArrayList<>(plugin.registry().all());
        }
        return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void spawn(CommandSender sender, String[] args) {
        if (denied(sender, "wildbosses.spawn")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", "?"),
                    Text.unparsed("reason", "usage: /wb spawn <id|random> [here|player]"));
            return;
        }
        BossDefinition def = args[1].equalsIgnoreCase("random") ? randomBoss(false) : plugin.registry().get(args[1]);
        if (def == null) {
            plugin.messages().send(sender, "unknown-boss", Text.unparsed("id", args[1]));
            return;
        }
        Location loc = resolveLocation(sender, args);
        if (loc == null) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", args[1]),
                    Text.unparsed("reason", "no location (run in-game or name a player)"));
            return;
        }
        ActiveBoss boss = plugin.bossManager().spawn(def, loc);
        if (boss == null) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", args[1]),
                    Text.unparsed("reason", "entity could not be spawned"));
            return;
        }
        plugin.messages().send(sender, "spawned",
                Text.parsed("boss", def.name()),
                Text.num("x", loc.getBlockX()),
                Text.num("y", loc.getBlockY()),
                Text.num("z", loc.getBlockZ()),
                Text.unparsed("world", worldName(loc)));
    }

    private Location resolveLocation(CommandSender sender, String[] args) {
        if (args.length >= 3 && !args[2].equalsIgnoreCase("here")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            return target == null ? null : target.getLocation();
        }
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        return null;
    }

    private void army(CommandSender sender, String[] args) {
        if (denied(sender, "wildbosses.spawn")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", "?"),
                    Text.unparsed("reason", "usage: /wb army <id|random> [here|player]"));
            return;
        }
        BossDefinition def = args[1].equalsIgnoreCase("random") ? randomBoss(true) : plugin.registry().get(args[1]);
        if (def == null) {
            plugin.messages().send(sender, "unknown-boss", Text.unparsed("id", args[1]));
            return;
        }
        if (!def.isArmy()) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", args[1]),
                    Text.unparsed("reason", "this boss has no army block"));
            return;
        }
        Location loc = resolveLocation(sender, args);
        if (loc == null) {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", args[1]),
                    Text.unparsed("reason", "no location (run in-game or name a player)"));
            return;
        }
        if (plugin.armyManager().start(def, loc)) {
            plugin.messages().send(sender, "army-started", Text.parsed("boss", def.name()),
                    Text.unparsed("world", worldName(loc)));
        } else {
            plugin.messages().send(sender, "spawn-failed", Text.unparsed("id", args[1]),
                    Text.unparsed("reason", "army could not be started"));
        }
    }

    private void list(CommandSender sender) {
        // Players get the visual bestiary (drops + chances); console gets a plain list.
        if (sender instanceof Player player && sender.hasPermission("wildbosses.list")) {
            new BestiaryMenu(plugin).open(player);
            return;
        }
        var registry = plugin.registry();
        plugin.messages().send(sender, "list-header", Text.num("count", registry.ids().size()));
        for (BossDefinition def : registry.all()) {
            sender.sendMessage(plugin.messages().plainMessage("list-entry",
                    Text.unparsed("id", def.id()),
                    Text.parsed("difficulty", def.difficulty().bracketedMini()),
                    Text.unparsed("worlds", worldsString(def))));
        }
    }

    private void active(CommandSender sender) {
        List<ActiveBoss> bosses = plugin.bossManager().active();
        var armies = plugin.armyManager().active();
        if (bosses.isEmpty() && armies.isEmpty()) {
            plugin.messages().send(sender, "no-active");
            return;
        }
        plugin.messages().send(sender, "active-header", Text.num("count", bosses.size() + armies.size()));
        long now = plugin.bossManager().currentTick();
        for (ActiveBoss boss : bosses) {
            Location loc = boss.location();
            Component line = plugin.messages().plainMessage("active-entry",
                    Text.parsed("boss", boss.def().name()),
                    Text.unparsed("world", worldName(loc)),
                    Text.num("x", loc.getBlockX()),
                    Text.num("y", loc.getBlockY()),
                    Text.num("z", loc.getBlockZ()),
                    Text.num("health", (int) Math.ceil(boss.entity().getHealth())));
            long remain = boss.fleeAtTick() - now;
            if (boss.fleeAtTick() > 0 && remain > 0) {
                line = line.append(Text.mm(" <dark_gray>(<gray>flees in <yellow>"
                        + Text.duration(remain / 20) + "</yellow>)</dark_gray>"));
            }
            sender.sendMessage(line);
        }
        for (ArmyEncounter army : armies) {
            Location loc = army.anchor();
            sender.sendMessage(Text.mm("<gray> - </gray>").append(Text.mm(army.def().name()))
                    .append(Text.mm("<gray> army @ <yellow>" + worldName(loc) + " "
                            + loc.getBlockX() + " " + loc.getBlockZ()
                            + " <dark_gray>(<gray>" + army.kills() + " slain</gray>)")));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "unknown-boss", Text.unparsed("id", "?"));
            return;
        }
        BossDefinition def = plugin.registry().get(args[1]);
        if (def == null) {
            plugin.messages().send(sender, "unknown-boss", Text.unparsed("id", args[1]));
            return;
        }
        sender.sendMessage(Text.mm("<gray>---- </gray>").append(Text.mm(def.name()))
                .append(Component.space()).append(def.difficulty().bracketed()));
        sender.sendMessage(Text.mm("<gray>Base entity: <white>" + def.baseEntity()
                + " <gray>| Health: <white>" + (int) def.stats().health()
                + " <gray>| Armor: <white>" + (int) def.stats().armor()));
        sender.sendMessage(Text.mm("<gray>Worlds: <white>" + worldsString(def)
                + " <gray>| Weight: <white>" + def.spawn().weight()));
        sender.sendMessage(Text.mm("<gray>Phases: <white>" + def.phases().size()
                + " <gray>| Skills: <white>" + def.skills().size()
                + " <gray>| Terrain: <white>" + (def.hasTerrain() ? "yes" : "no")
                + " <gray>| Army: <white>" + (def.isArmy() ? "yes" : "no")));
    }

    private void gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (denied(sender, "wildbosses.gui")) {
            return;
        }
        new MainMenu(plugin).open(player);
    }

    private void update(CommandSender sender) {
        if (denied(sender, "wildbosses.admin")) {
            return;
        }
        Updater.run(plugin, sender);
    }

    private void killAll(CommandSender sender) {
        if (denied(sender, "wildbosses.admin")) {
            return;
        }
        int n = plugin.bossManager().killAll();
        n += plugin.armyManager().count();
        plugin.armyManager().shutdown();
        plugin.messages().send(sender, "killed-all", Text.num("count", n));
    }

    // ---- helpers --------------------------------------------------------------------------

    private boolean denied(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        plugin.messages().sendList(sender, "help");
    }

    private static String worldName(Location loc) {
        return Text.worldName(loc);
    }

    private static String worldsString(BossDefinition def) {
        StringJoiner sj = new StringJoiner(", ");
        for (World.Environment env : def.spawn().environments()) {
            sj.add(switch (env) {
                case NORMAL -> "Overworld";
                case NETHER -> "Nether";
                case THE_END -> "End";
                default -> env.name();
            });
        }
        return sj.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("spawn") || sub.equals("army")) {
                List<String> opts = new ArrayList<>();
                opts.add("random");
                opts.addAll(plugin.registry().ids());
                return filter(opts, args[1]);
            }
            if (sub.equals("info")) {
                return filter(new ArrayList<>(plugin.registry().ids()), args[1]);
            }
            if (sub.equals("restore")) {
                return filter(List.of("default"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            List<String> opts = new ArrayList<>();
            opts.add("here");
            for (Player p : Bukkit.getOnlinePlayers()) {
                opts.add(p.getName());
            }
            return filter(opts, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String partial) {
        String p = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
