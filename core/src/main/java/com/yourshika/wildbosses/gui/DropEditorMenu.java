package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edit a boss' item drops without touching the YAML by hand: adjust each drop's chance and toggle
 * whether it triggers a chat announcement. Operates on the raw {@code drops.items} list so every
 * other field (name, lore, enchants, glow, model data) is preserved on save.
 */
public final class DropEditorMenu extends Menu {

    private final String bossId;
    private final File file;
    private final YamlConfiguration yml;
    private final List<Map<String, Object>> items = new ArrayList<>();

    public DropEditorMenu(WildBossesPlugin plugin, String bossId) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Drops: " + bossId);
        this.bossId = bossId;
        this.file = new File(plugin.getDataFolder(), "bosses/" + bossId + ".yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yml.getMapList("drops.items")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            items.add(copy);
        }
    }

    @Override
    protected void build() {
        if (items.isEmpty()) {
            set(22, icon(Material.BARRIER, "<red>This boss has no item drops."), null);
        }
        for (int i = 0; i < items.size() && i < 45; i++) {
            final int index = i;
            Map<String, Object> item = items.get(i);
            Material mat = matOf(item.get("item"));
            double chance = toDouble(item.get("chance"), 1.0);
            boolean announce = Boolean.TRUE.equals(item.get("announce"));
            String name = item.get("name") != null ? String.valueOf(item.get("name")) : "<white>" + mat.name();
            set(i, icon(mat, name,
                            "<gray>Chance: <yellow>" + Math.round(chance * 100) + "%",
                            "<gray>Announce: " + (announce ? "<green>on" : "<red>off"),
                            " ",
                            "<gray>Left-click <green>+5% <gray>chance",
                            "<gray>Right-click <red>-5% <gray>chance",
                            "<gray>Shift-click <aqua>toggle announce"),
                    e -> {
                        Map<String, Object> it = items.get(index);
                        if (e.isShiftClick()) {
                            it.put("announce", !Boolean.TRUE.equals(it.get("announce")));
                        } else if (e.isRightClick()) {
                            it.put("chance", round2(Math.max(0.0, toDouble(it.get("chance"), 1.0) - 0.05)));
                        } else {
                            it.put("chance", round2(Math.min(1.0, toDouble(it.get("chance"), 1.0) + 0.05)));
                        }
                        rebuild();
                    });
        }

        set(45, icon(Material.ARROW, "<yellow>Back"),
                e -> new BossEditorMenu(plugin, bossId).open((Player) e.getWhoClicked()));
        set(49, icon(Material.LIME_CONCRETE, "<green><bold>Save",
                        "<gray>Write drop chances / announce flags to",
                        "<yellow>bosses/" + bossId + ".yml <gray>and reload."),
                e -> save((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void save(Player player) {
        yml.set("drops.items", items);
        try {
            yml.save(file);
            plugin.reloadAll();
            player.sendMessage(Text.mm("<green>Saved drops for <yellow>" + bossId + "<green> and reloaded."));
            new DropEditorMenu(plugin, bossId).open(player);
        } catch (IOException ex) {
            player.sendMessage(Text.mm("<red>Failed to save: " + ex.getMessage()));
        }
    }

    private static Material matOf(Object raw) {
        if (raw == null) {
            return Material.PAPER;
        }
        Material m = Material.matchMaterial(String.valueOf(raw));
        return m == null ? Material.PAPER : m;
    }

    private static double toDouble(Object raw, double def) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw != null) {
            try {
                return Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
