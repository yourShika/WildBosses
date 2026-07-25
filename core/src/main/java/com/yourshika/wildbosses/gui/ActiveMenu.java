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
 * <p>Two modes:
 * <ul>
 *   <li><b>Read-only</b> (a normal player running {@code /wb active}): purely informational - shows
 *       each encounter and where it is. No teleport, no removal, and NO way into the admin menu.</li>
 *   <li><b>Manage</b> (a player with the admin-GUI permission, or reached from the admin main menu):
 *       left-click teleports, right-click removes/terminates, and a Back button returns to the menu.</li>
 * </ul>
 * This split is a security boundary: {@code /wb active} is available to everyone, so the interactive
 * controls (and the door back into the admin GUI) must never be handed to players without permission.
 */
public final class ActiveMenu extends Menu {

    private final boolean manage;

    public ActiveMenu(WildBossesPlugin plugin, boolean manage) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Active");
        this.manage = manage;
    }

    @Override
    protected void build() {
        int slot = 0;
        for (ActiveBoss boss : plugin.bossManager().active()) {
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
        for (ArmyEncounter army : plugin.armyManager().active()) {
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
        // The Back button leads into the admin main menu, so only show it to players who may use it.
        if (manage) {
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
}
