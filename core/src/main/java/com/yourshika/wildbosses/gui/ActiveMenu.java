package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.army.ArmyEncounter;
import com.yourshika.wildbosses.boss.ActiveBoss;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Lists active bosses and armies. Left-click teleports; right-click removes/terminates. */
public final class ActiveMenu extends Menu {

    public ActiveMenu(WildBossesPlugin plugin) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Active");
    }

    @Override
    protected void build() {
        int slot = 0;
        for (ActiveBoss boss : plugin.bossManager().active()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, bossIcon(boss), e -> {
                Player player = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    plugin.bossManager().killOne(boss);
                    rebuild();
                } else {
                    player.teleport(boss.location());
                    player.closeInventory();
                }
            });
        }
        for (ArmyEncounter army : plugin.armyManager().active()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, armyIcon(army), e -> {
                Player player = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    plugin.armyManager().terminate(army);
                    rebuild();
                } else {
                    player.teleport(army.anchor());
                    player.closeInventory();
                }
            });
        }
        // Active lunar events (Blood/Crystal/Harvest/Eclipse) - one entry per world.
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
        set(49, icon(Material.ARROW, "<yellow>Back"), e -> new MainMenu(plugin).open((Player) e.getWhoClicked()));
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
                        : "<dark_gray>No time cap",
                lunar.isForced(world) ? "<dark_gray>(started by an admin)" : "<dark_gray>(natural)",
                " ",
                "<dark_gray>Stop with /wb lunar stop " + world.getName());
    }

    private org.bukkit.inventory.ItemStack bossIcon(ActiveBoss boss) {
        Material egg = Material.matchMaterial(boss.def().baseEntity().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        Location loc = boss.location();
        long remain = boss.fleeAtTick() - plugin.bossManager().currentTick();
        String flee = (boss.fleeAtTick() > 0 && remain > 0)
                ? "<gray>" + tr("Flees in") + " <yellow>" + com.yourshika.wildbosses.util.Text.duration(remain / 20)
                : "<dark_gray>No flee timer";
        return icon(egg != null ? egg : Material.NETHER_STAR,
                plugin.messages().tr(boss.def().name()) + " "
                        + boss.def().difficulty().bracketedMini(plugin.messages().tr(boss.def().difficulty().label())),
                "<gray>" + tr("Health") + " <white>" + (int) Math.ceil(boss.entity().getHealth()) + "<gray>/<white>" + (int) boss.maxHealth(),
                "<gray>" + tr("At") + " <yellow>" + worldName(loc) + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                flee,
                " ",
                "<yellow>Left-click <gray>teleport",
                "<yellow>Right-click <gray>remove");
    }

    private org.bukkit.inventory.ItemStack armyIcon(ArmyEncounter army) {
        Location loc = army.anchor();
        return icon(Material.ZOMBIE_HEAD,
                plugin.messages().tr(army.def().name()) + " <gray>(army)",
                "<gray>" + tr("Slain") + " <white>" + army.kills(),
                "<gray>" + tr("At") + " <yellow>" + worldName(loc) + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                " ",
                "<yellow>Left-click <gray>teleport",
                "<yellow>Right-click <gray>terminate");
    }

    private static String worldName(Location loc) {
        return com.yourshika.wildbosses.util.Text.worldName(loc);
    }
}
