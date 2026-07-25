package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Toggle spawns/worlds and adjust the common tuning values, persisted to config.yml. Two pages. */
public final class SettingsMenu extends Menu {

    private final int page;

    public SettingsMenu(WildBossesPlugin plugin) {
        this(plugin, 0);
    }

    public SettingsMenu(WildBossesPlugin plugin, int page) {
        super(plugin, 27, "<dark_gray>WildBosses <gray>- Settings " + (page == 0 ? "1/2" : "2/2"));
        this.page = page;
    }

    @Override
    protected void build() {
        if (page == 0) {
            buildPage1();
        } else {
            buildPage2();
        }
        set(26, icon(Material.ARROW, "<yellow>Back"), e -> new MainMenu(plugin).open((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void buildPage1() {
        FileConfiguration c = plugin.getConfig();
        set(10, toggle("Random spawns", c.getBoolean("settings.random-spawns", true)),
                e -> toggle("settings.random-spawns"));
        set(12, toggle("Overworld spawns", c.getBoolean("worlds.OVERWORLD", true)),
                e -> toggle("worlds.OVERWORLD"));
        set(13, toggle("Nether spawns", c.getBoolean("worlds.NETHER", true)),
                e -> toggle("worlds.NETHER"));
        set(14, toggle("End spawns", c.getBoolean("worlds.THE_END", true)),
                e -> toggle("worlds.THE_END"));

        int minM = c.getInt("settings.spawn-interval.min-minutes", 10);
        int maxM = c.getInt("settings.spawn-interval.max-minutes", 30);
        set(15, adj(Material.CLOCK, "Spawn interval min", minM + "m",
                "<gray>Random wait each cycle is between min and max."),
                e -> adjustInt("settings.spawn-interval.min-minutes", e.isLeftClick() ? 1 : -1, 1));
        set(16, adj(Material.CLOCK, "Spawn interval max", maxM + "m", null),
                e -> adjustInt("settings.spawn-interval.max-minutes", e.isLeftClick() ? 1 : -1, 1));

        set(20, adj(Material.DRAGON_EGG, "Max active bosses",
                String.valueOf(c.getInt("settings.max-active-bosses", 5)), null),
                e -> adjustInt("settings.max-active-bosses", e.isLeftClick() ? 1 : -1, 1));

        set(24, icon(Material.BOOK, "<gold>More settings →",
                "<gray>Distance, scaling, lifetime, drops,",
                "<gray>lunar chance, alerts.",
                "<yellow>Click <gray>to open page 2"), e -> new SettingsMenu(plugin, 1).open((Player) e.getWhoClicked()));
    }

    private void buildPage2() {
        FileConfiguration c = plugin.getConfig();
        set(10, adj(Material.COMPASS, "Min distance between bosses",
                (int) c.getDouble("settings.min-distance-between-bosses", 200) + "b",
                "<gray>Left +25 / Right -25"),
                e -> adjustDouble("settings.min-distance-between-bosses", e.isLeftClick() ? 25 : -25, 0));
        set(11, toggle("Player scaling", c.getBoolean("settings.scaling.enabled", true)),
                e -> toggle("settings.scaling.enabled"));
        set(12, toggle("Boss lifetime (flee)", c.getBoolean("settings.boss-lifetime.enabled", true)),
                e -> toggle("settings.boss-lifetime.enabled"));
        set(13, adj(Material.EXPERIENCE_BOTTLE, "Max adds per boss",
                String.valueOf(c.getInt("settings.max-adds-per-boss", 30)), null),
                e -> adjustInt("settings.max-adds-per-boss", e.isLeftClick() ? 5 : -5, 1));

        set(15, adj(Material.CHEST_MINECART, "Drop count min",
                String.valueOf(c.getInt("rewards.drop-count.min", 1)), null),
                e -> adjustInt("rewards.drop-count.min", e.isLeftClick() ? 1 : -1, 0));
        set(16, adj(Material.CHEST_MINECART, "Drop count max",
                String.valueOf(c.getInt("rewards.drop-count.max", 3)), null),
                e -> adjustInt("rewards.drop-count.max", e.isLeftClick() ? 1 : -1, 1));
        set(17, toggle("Weight loot by damage", c.getBoolean("rewards.weight-by-damage", false)),
                e -> toggle("rewards.weight-by-damage"));

        int lunarPct = (int) Math.round(c.getDouble("lunar-events.chance", 0.12) * 100);
        set(19, adj(Material.CLOCK, "Lunar event chance", lunarPct + "%",
                "<gray>Per-night chance. Left +2% / Right -2%"),
                e -> adjustDoublePct("lunar-events.chance", e.isLeftClick() ? 0.02 : -0.02));
        set(20, toggle("Lunar events", c.getBoolean("lunar-events.enabled", true)),
                e -> toggle("lunar-events.enabled"));
        set(21, toggle("Spawn alert (actionbar)", c.getBoolean("broadcast.spawn-alert", true)),
                e -> toggle("broadcast.spawn-alert"));

        set(24, icon(Material.BOOK, "<gold>← Page 1",
                "<yellow>Click <gray>to go back"), e -> new SettingsMenu(plugin, 0).open((Player) e.getWhoClicked()));
    }

    private org.bukkit.inventory.ItemStack toggle(String label, boolean on) {
        return icon(on ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow>" + label + ": " + (on ? "<green>on" : "<red>off"),
                "<gray>Click to toggle");
    }

    private org.bukkit.inventory.ItemStack adj(Material mat, String label, String value, String extra) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        if (extra != null) {
            lore.add(extra);
        }
        lore.add("<gray>Left-click <green>+  <gray>Right-click <red>-");
        return icon(mat, "<yellow>" + label + ": <white>" + value, lore.toArray(new String[0]));
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

    private void adjustDouble(String path, double delta, double min) {
        double value = Math.max(min, plugin.getConfig().getDouble(path) + delta);
        plugin.getConfig().set(path, value);
        apply();
    }

    private void adjustDoublePct(String path, double delta) {
        double value = Math.max(0, Math.min(1, plugin.getConfig().getDouble(path) + delta));
        plugin.getConfig().set(path, value);
        apply();
    }

    private void apply() {
        plugin.saveConfig();
        plugin.reloadConfigOnly(); // config-only: don't re-parse every boss YAML on a single toggle
        rebuild();
    }
}
