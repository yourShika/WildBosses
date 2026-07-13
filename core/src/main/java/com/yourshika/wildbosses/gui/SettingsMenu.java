package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Toggle random spawns / worlds and adjust interval and limits, persisted to config.yml. */
public final class SettingsMenu extends Menu {

    public SettingsMenu(WildBossesPlugin plugin) {
        super(plugin, 27, "<dark_gray>WildBosses <gray>- Settings");
    }

    @Override
    protected void build() {
        FileConfiguration c = plugin.getConfig();

        set(10, toggle("Random spawns", c.getBoolean("settings.random-spawns", true)),
                e -> toggle("settings.random-spawns"));
        set(12, toggle("Overworld spawns", c.getBoolean("worlds.OVERWORLD", true)),
                e -> toggle("worlds.OVERWORLD"));
        set(13, toggle("Nether spawns", c.getBoolean("worlds.NETHER", true)),
                e -> toggle("worlds.NETHER"));
        set(14, toggle("End spawns", c.getBoolean("worlds.THE_END", true)),
                e -> toggle("worlds.THE_END"));

        set(16, icon(Material.CLOCK, "<yellow>Spawn interval: <white>" + c.getInt("settings.spawn-interval-seconds", 600) + "s",
                "<gray>Left-click <green>+60s", "<gray>Right-click <red>-60s"),
                e -> adjustInt("settings.spawn-interval-seconds", e.isLeftClick() ? 60 : -60, 20));

        set(22, icon(Material.DRAGON_EGG, "<yellow>Max active bosses: <white>" + c.getInt("settings.max-active-bosses", 5),
                "<gray>Left-click <green>+1", "<gray>Right-click <red>-1"),
                e -> adjustInt("settings.max-active-bosses", e.isLeftClick() ? 1 : -1, 1));

        set(26, icon(Material.ARROW, "<yellow>Back"), e -> new MainMenu(plugin).open((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack toggle(String label, boolean on) {
        return icon(on ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow>" + label + ": " + (on ? "<green>on" : "<red>off"),
                "<gray>Click to toggle");
    }

    private void toggle(String path) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, true));
        apply();
    }

    private void adjustInt(String path, int delta, int min) {
        int value = Math.max(min, plugin.getConfig().getInt(path) + delta);
        plugin.getConfig().set(path, value);
        apply();
    }

    private void apply() {
        plugin.saveConfig();
        plugin.reloadAll();
        rebuild();
    }
}
