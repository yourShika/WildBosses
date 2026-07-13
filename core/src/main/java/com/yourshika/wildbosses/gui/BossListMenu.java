package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Browse every registered boss. Left-click spawns, right-click starts an army, shift-click edits. */
public final class BossListMenu extends Menu {

    public BossListMenu(WildBossesPlugin plugin) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Bosses");
    }

    @Override
    protected void build() {
        int slot = 0;
        for (BossDefinition def : plugin.registry().all()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, iconFor(def), e -> {
                Player player = (Player) e.getWhoClicked();
                if (e.isShiftClick()) {
                    new BossEditorMenu(plugin, def.id()).open(player);
                    return;
                }
                Location loc = player.getLocation();
                if (e.isRightClick() && def.isArmy()) {
                    plugin.armyManager().start(def, loc);
                } else if (def.isArmy() && !e.isRightClick()) {
                    // left-click on an army spawns just the leader for testing
                    plugin.bossManager().spawn(def, loc);
                } else {
                    plugin.bossManager().spawn(def, loc);
                }
                player.closeInventory();
            });
        }
        set(49, icon(Material.ARROW, "<yellow>Back"), e -> new MainMenu(plugin).open((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack iconFor(BossDefinition def) {
        Material egg = Material.matchMaterial(def.baseEntity().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        Material material = egg != null ? egg : Material.NETHER_STAR;
        String title = def.title() == null ? "" : def.title();
        return icon(material,
                def.name() + " " + def.difficulty().bracketedMini(),
                "<gray>" + title,
                "<dark_gray>Health <gray>" + (int) def.stats().health()
                        + " <dark_gray>Armor <gray>" + (int) def.stats().armor(),
                "<dark_gray>Weight <gray>" + def.spawn().weight()
                        + " <dark_gray>Terrain <gray>" + (def.hasTerrain() ? "yes" : "no")
                        + " <dark_gray>Model <gray>" + (def.hasModel() ? def.model() : "none"),
                " ",
                "<yellow>Left-click <gray>spawn here",
                def.isArmy() ? "<yellow>Right-click <gray>start army" : "<dark_gray>(not an army)",
                "<yellow>Shift-click <gray>edit");
    }
}
