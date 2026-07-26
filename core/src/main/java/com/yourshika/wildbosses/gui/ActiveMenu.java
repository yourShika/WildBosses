package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.army.ArmyEncounter;
import com.yourshika.wildbosses.boss.ActiveBoss;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live overview of active bosses, armies and lunar events (all with coordinates).
 *
 * <p>Two independent flags:
 * <ul>
 *   <li><b>manage</b> - left-click teleports and right-click removes/terminates. Granted to players
 *       with the admin-GUI permission.</li>
 *   <li><b>showBack</b> - whether the Back button (which opens the admin {@link MainMenu}) is shown.
 *       This is <em>only</em> true when the menu was opened <b>from</b> the admin main menu, so the
 *       door back into the admin GUI is never present in the standalone {@code /wb active} view -
 *       not even for admins. {@code /wb active} is a read-only-ish info screen; management lives
 *       under {@code /wb gui}.</li>
 * </ul>
 * This split is a security boundary: {@code /wb active} is available to everyone, so the door into
 * the admin GUI must never appear there; you only ever reach {@link MainMenu} via the perm-gated
 * {@code /wb gui}.
 */
public final class ActiveMenu extends Menu {

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final boolean manage;
    private final boolean showBack;
    private Player viewer;

    public ActiveMenu(WildBossesPlugin plugin, boolean manage, boolean showBack) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Active");
        this.manage = manage;
        this.showBack = showBack;
    }

    @Override
    public void open(Player player) {
        this.viewer = player; // captured so entries can be sorted by / labelled with distance to them
        super.open(player);
    }

    @Override
    protected void build() {
        int slot = 0;
        java.util.List<ActiveBoss> bosses = new java.util.ArrayList<>(plugin.bossManager().active());
        bosses.sort(java.util.Comparator.comparingDouble(b -> distSq(b.location())));
        java.util.List<ArmyEncounter> armies = new java.util.ArrayList<>(plugin.armyManager().active());
        armies.sort(java.util.Comparator.comparingDouble(a -> distSq(a.anchor())));
        for (ActiveBoss boss : bosses) {
            if (slot >= 45) {
                break;
            }
            set(slot++, bossIcon(boss), manage ? e -> {
                Player player = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    plugin.bossManager().killOne(boss);
                    rebuild();
                } else {
                    player.teleport(boss.location());
                    player.closeInventory();
                }
            } : null);
        }
        for (ArmyEncounter army : armies) {
            if (slot >= 45) {
                break;
            }
            set(slot++, armyIcon(army), manage ? e -> {
                Player player = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    plugin.armyManager().terminate(army);
                    rebuild();
                } else {
                    player.teleport(army.anchor());
                    player.closeInventory();
                }
            } : null);
        }
        // Active lunar events (Blood/Crystal/Harvest/Eclipse) - one entry per world (always read-only).
        int lunarSlot = 45;
        for (var entry : plugin.lunarEvents() == null
                ? java.util.Map.<org.bukkit.World, String>of().entrySet()
                : plugin.lunarEvents().activeEvents().entrySet()) {
            if (lunarSlot > 47) {
                break;
            }
            set(lunarSlot++, lunarIcon(entry.getKey(), entry.getValue()), null);
        }

        if (slot == 0 && lunarSlot == 45) {
            set(22, icon(Material.BARRIER, "<gray>No active encounters"), null);
        }
        // The Back button leads into the admin main menu, so only show it when we actually came from
        // there (i.e. /wb gui -> Active). It is never shown in the standalone /wb active view.
        if (showBack) {
            set(49, icon(Material.ARROW, "<yellow>Back"), e -> new MainMenu(plugin).open((Player) e.getWhoClicked()));
        }
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack lunarIcon(org.bukkit.World world, String type) {
        var lunar = plugin.lunarEvents();
        long remain = lunar.remainingSeconds(world);
        Material mat = switch (type) {
            case "crystalmoon" -> Material.AMETHYST_CLUSTER;
            case "harvestmoon" -> Material.GOLDEN_CARROT;
            case "eclipse" -> Material.BLACK_CONCRETE;
            default -> Material.REDSTONE_BLOCK;
        };
        return icon(mat, lunar.displayName(type),
                "<gray>" + tr("Lunar event") + " <dark_gray>(" + type + ")",
                "<gray>" + tr("World") + " <yellow>" + world.getName(),
                remain >= 0
                        ? "<gray>" + tr("Ends in") + " <yellow>"
                                + com.yourshika.wildbosses.util.Text.duration(remain)
                        : "<dark_gray>" + tr("No time cap"),
                "<dark_gray>" + tr(lunar.isForced(world) ? "(started by an admin)" : "(natural)"),
                " ",
                "<dark_gray>" + tr("Stop with /wb lunar stop") + " " + world.getName());
    }

    private org.bukkit.inventory.ItemStack bossIcon(ActiveBoss boss) {
        Material egg = Material.matchMaterial(boss.def().baseEntity().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        Location loc = boss.location();
        long remain = boss.fleeAtTick() - plugin.bossManager().currentTick();
        String flee = (boss.fleeAtTick() > 0 && remain > 0)
                ? "<gray>" + tr("Flees in") + " <yellow>" + com.yourshika.wildbosses.util.Text.duration(remain / 20)
                : "<dark_gray>No flee timer";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + tr("Health") + " <white>" + (int) Math.ceil(boss.entity().getHealth())
                + "<gray>/<white>" + (int) boss.maxHealth());
        lore.add("<gray>" + tr("At") + " <yellow>" + worldName(loc) + " "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        String dl = distanceLine(loc);
        if (dl != null) {
            lore.add(dl);
        }
        lore.add(flee);
        if (manage) {
            lore.add(" ");
            lore.add("<yellow>Left-click <gray>teleport");
            lore.add("<yellow>Right-click <gray>remove");
        }
        return icon(egg != null ? egg : Material.NETHER_STAR,
                plugin.messages().tr(boss.def().name()) + " "
                        + boss.def().difficulty().bracketedMini(plugin.messages().tr(boss.def().difficulty().label())),
                lore.toArray(new String[0]));
    }

    private org.bukkit.inventory.ItemStack armyIcon(ArmyEncounter army) {
        Location loc = army.anchor();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + tr("Slain") + " <white>" + army.kills());
        lore.add("<gray>" + tr("At") + " <yellow>" + worldName(loc) + " "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        String dl = distanceLine(loc);
        if (dl != null) {
            lore.add(dl);
        }
        if (manage) {
            lore.add(" ");
            lore.add("<yellow>Left-click <gray>teleport");
            lore.add("<yellow>Right-click <gray>terminate");
        }
        return icon(Material.ZOMBIE_HEAD,
                plugin.messages().tr(army.def().name()) + " <gray>(army)",
                lore.toArray(new String[0]));
    }

    private static String worldName(Location loc) {
        return com.yourshika.wildbosses.util.Text.worldName(loc);
    }

    /** Squared distance from the viewer to {@code loc} (MAX if unknown / another world) - for sorting. */
    private double distSq(Location loc) {
        if (viewer == null || loc.getWorld() == null || !loc.getWorld().equals(viewer.getWorld())) {
            return Double.MAX_VALUE;
        }
        return loc.distanceSquared(viewer.getLocation());
    }

    /** A "120m NE" (or "another world") line relative to the viewer, or null if there's no viewer. */
    private String distanceLine(Location loc) {
        if (viewer == null || loc.getWorld() == null) {
            return null;
        }
        if (!loc.getWorld().equals(viewer.getWorld())) {
            return "<dark_gray>(" + tr("another world") + ")";
        }
        int dist = (int) viewer.getLocation().distance(loc);
        return "<gray>" + tr("Distance") + " <yellow>" + dist + "m " + direction(viewer.getLocation(), loc);
    }

    private static String direction(Location from, Location to) {
        double deg = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        return DIRS[(int) Math.round((((deg % 360) + 360) % 360) / 45.0) % 8];
    }
}
