package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only "bestiary" for everyone: browse every boss and click through to its drop table with
 * chances. Opened by {@code /wb list} for players. Shows each viewer's personal "defeated N times"
 * count, and (if {@code settings.bestiary-discovery-lock} is on) hides bosses not yet defeated.
 */
public final class BestiaryMenu extends Menu {

    private Player viewer;

    public BestiaryMenu(WildBossesPlugin plugin) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Bestiary");
    }

    @Override
    public void open(Player player) {
        this.viewer = player;
        super.open(player);
    }

    @Override
    protected void build() {
        int slot = 0;
        for (BossDefinition def : plugin.registry().all()) {
            if (slot >= 45) {
                break;
            }
            boolean locked = isLocked(def);
            set(slot++, bossIcon(def, locked), locked ? null
                    : e -> new BestiaryDropsMenu(plugin, def.id()).open((Player) e.getWhoClicked()));
        }
        if (slot == 0) {
            set(22, icon(Material.BARRIER, "<red>No bosses loaded."), null);
        }
        set(49, icon(Material.BARRIER, "<red>Close"), e -> e.getWhoClicked().closeInventory());
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private boolean isLocked(BossDefinition def) {
        return plugin.config().bestiaryDiscoveryLock() && viewer != null
                && plugin.playerStats().kills(viewer.getUniqueId(), def.id()) == 0;
    }

    private ItemStack bossIcon(BossDefinition def, boolean locked) {
        String diff = def.difficulty().bracketedMini(plugin.messages().tr(def.difficulty().label()));
        if (locked) {
            return icon(Material.GRAY_DYE, "<dark_gray>??? " + diff,
                    "<gray>" + tr("Undiscovered"),
                    "<dark_gray>" + tr("Defeat this boss to reveal it."));
        }
        int kills = viewer == null ? 0 : plugin.playerStats().kills(viewer.getUniqueId(), def.id());
        Material egg = Material.matchMaterial(def.baseEntity().name().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        int drops = def.drops().items().size() + def.drops().rawDrops().size() + def.drops().commandRewards().size();
        List<String> lore = new ArrayList<>();
        lore.add(def.title() == null ? "<gray>" : "<gray><italic>" + stripTags(plugin.messages().tr(def.title())));
        lore.add(" ");
        lore.add("<gray>" + tr("Health") + " <white>" + (int) def.stats().health()
                + " <gray>· " + tr("Armor") + " <white>" + (int) def.stats().armor());
        lore.add("<gray>" + tr("Type:") + " <white>" + tr(def.isArmy() ? "Army" : "Boss"));
        lore.add("<gray>" + tr("Drops:") + " <white>" + drops + " " + tr("entries"));
        lore.add("<gray>" + tr("Defeated") + " <yellow>" + kills + "<gray> " + tr("times"));
        lore.add(" ");
        lore.add("<yellow>Click <gray>to view the drop table");
        return icon(egg != null ? egg : Material.NETHER_STAR,
                plugin.messages().tr(def.name()) + " " + diff, lore.toArray(new String[0]));
    }

    private static String stripTags(String mini) {
        return mini == null ? "" : mini.replaceAll("<[^>]+>", "");
    }
}
