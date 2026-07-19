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
        String title = def.title() == null ? "" : plugin.messages().tr(def.title());
        return icon(material,
                plugin.messages().tr(def.name()) + " "
                        + def.difficulty().bracketedMini(plugin.messages().tr(def.difficulty().label())),
                "<gray>" + title,
                "<dark_gray>" + tr("Health") + " <gray>" + (int) def.stats().health()
                        + " <dark_gray>" + tr("Armor") + " <gray>" + (int) def.stats().armor(),
                "<dark_gray>" + tr("Weight") + " <gray>" + def.spawn().weight()
                        + " <dark_gray>" + tr("Terrain") + " <gray>" + tr(def.hasTerrain() ? "yes" : "no"),
                " ",
                "<yellow>Left-click <gray>spawn here",
                def.isArmy() ? "<yellow>Right-click <gray>start army" : "<dark_gray>(not an army)",
                "<yellow>Shift-click <gray>edit");
    }
}
