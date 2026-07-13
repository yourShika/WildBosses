package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Root admin menu: Bosses / Active encounters / Settings. */
public final class MainMenu extends Menu {

    public MainMenu(WildBossesPlugin plugin) {
        super(plugin, 27, "<dark_gray>WildBosses <gray>- <gradient:#f8b500:#fceabb>Admin</gradient>");
    }

    @Override
    protected void build() {
        set(11, icon(Material.NETHER_STAR, "<gold>Bosses",
                "<gray>Browse, spawn and edit bosses.",
                "<yellow>Click to open."), e -> new BossListMenu(plugin).open((Player) e.getWhoClicked()));

        set(13, icon(Material.DIAMOND_SWORD, "<red>Active Encounters",
                "<gray>View, teleport to and remove",
                "<gray>active bosses and armies.",
                "<yellow>Click to open."), e -> new ActiveMenu(plugin).open((Player) e.getWhoClicked()));

        set(15, icon(Material.COMPARATOR, "<aqua>Settings",
                "<gray>Toggle random spawns, worlds,",
                "<gray>interval and limits.",
                "<yellow>Click to open."), e -> new SettingsMenu(plugin).open((Player) e.getWhoClicked()));

        filler(Material.BLACK_STAINED_GLASS_PANE);
    }
}
