package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Read-only "bestiary" for everyone: browse every boss and click through to its drop table with
 * chances. Opened by {@code /wb list} for players (console still gets a plain text list).
 */
public final class BestiaryMenu extends Menu {

    public BestiaryMenu(WildBossesPlugin plugin) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Bestiary");
    }

    @Override
    protected void build() {
        int slot = 0;
        for (BossDefinition def : plugin.registry().all()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, bossIcon(def),
                    e -> new BestiaryDropsMenu(plugin, def.id()).open((Player) e.getWhoClicked()));
        }
        if (slot == 0) {
            set(22, icon(Material.BARRIER, "<red>No bosses loaded."), null);
        }
        set(49, icon(Material.BARRIER, "<red>Close"), e -> e.getWhoClicked().closeInventory());
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack bossIcon(BossDefinition def) {
        Material egg = Material.matchMaterial(def.baseEntity().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        int drops = def.drops().items().size() + def.drops().rawDrops().size() + def.drops().commandRewards().size();
        return icon(egg != null ? egg : Material.NETHER_STAR,
                plugin.messages().tr(def.name()) + " "
                        + def.difficulty().bracketedMini(plugin.messages().tr(def.difficulty().label())),
                def.title() == null ? "<gray>" : "<gray><italic>" + stripTags(plugin.messages().tr(def.title())),
                " ",
                "<gray>" + tr("Health") + " <white>" + (int) def.stats().health()
                        + " <gray>· " + tr("Armor") + " <white>" + (int) def.stats().armor(),
                "<gray>" + tr("Type:") + " <white>" + tr(def.isArmy() ? "Army" : "Boss"),
                "<gray>" + tr("Drops:") + " <white>" + drops + " " + tr("entries"),
                " ",
                "<yellow>Click <gray>to view the drop table");
    }

    private static String stripTags(String mini) {
        return mini == null ? "" : mini.replaceAll("<[^>]+>", "");
    }
}
