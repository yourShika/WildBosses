package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.difficulty.Difficulty;
import com.yourshika.wildbosses.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

/**
 * Quick-edit the common fields of a boss and write them back to its YAML. Deep skill/ability
 * scripting stays in the YAML file; this covers difficulty, stats, spawn weight and terrain toggle.
 */
public final class BossEditorMenu extends Menu {

    private final String bossId;

    private Difficulty difficulty;
    private double health;
    private double armor;
    private double damage;
    private int weight;
    private boolean terrainEnabled;
    private boolean loaded;

    public BossEditorMenu(WildBossesPlugin plugin, String bossId) {
        super(plugin, 45, "<dark_gray>WildBosses <gray>- Edit " + bossId);
        this.bossId = bossId;
        loadFrom(plugin.registry().get(bossId));
    }

    private void loadFrom(BossDefinition def) {
        if (def == null) {
            return;
        }
        difficulty = def.difficulty();
        health = def.stats().health();
        armor = def.stats().armor();
        damage = def.stats().attackDamage();
        weight = def.spawn().weight();
        terrainEnabled = def.hasTerrain();
        loaded = true;
    }

    @Override
    protected void build() {
        if (!loaded) {
            set(22, icon(Material.BARRIER, "<red>Unknown boss: " + bossId), null);
            set(40, icon(Material.ARROW, "<yellow>Back"), e -> new BossListMenu(plugin).open((Player) e.getWhoClicked()));
            return;
        }

        set(10, icon(Material.NETHER_STAR, "<gold>Difficulty: " + difficulty.bracketedMini(),
                "<yellow>Click <gray>to cycle"), e -> {
            Difficulty[] values = Difficulty.values();
            difficulty = values[(difficulty.ordinal() + 1) % values.length];
            rebuild();
        });

        set(12, stat(Material.RED_DYE, "Health", (int) health), e -> {
            health = Math.max(1, health + (e.isLeftClick() ? 10 : -10));
            rebuild();
        });
        set(14, stat(Material.IRON_CHESTPLATE, "Armor", (int) armor), e -> {
            armor = Math.max(0, armor + (e.isLeftClick() ? 1 : -1));
            rebuild();
        });
        set(16, stat(Material.IRON_SWORD, "Damage", (int) damage), e -> {
            damage = Math.max(0, damage + (e.isLeftClick() ? 1 : -1));
            rebuild();
        });
        set(20, stat(Material.EXPERIENCE_BOTTLE, "Spawn weight", weight), e -> {
            weight = Math.max(0, weight + (e.isLeftClick() ? 1 : -1));
            rebuild();
        });
        set(22, icon(terrainEnabled ? Material.MYCELIUM : Material.DIRT,
                "<aqua>Terrain: " + (terrainEnabled ? "<green>on" : "<red>off"),
                "<gray>Corrupt nearby ground (frontier only).",
                "<yellow>Click <gray>to toggle"), e -> {
            terrainEnabled = !terrainEnabled;
            rebuild();
        });
        set(24, icon(Material.CHEST, "<gold>Drops",
                "<gray>Adjust each drop's chance and",
                "<gray>toggle its chat announcement.",
                "<yellow>Click <gray>to open"), e -> new DropEditorMenu(plugin, bossId).open((Player) e.getWhoClicked()));

        set(30, icon(Material.LIME_CONCRETE, "<green><bold>Save",
                "<gray>Write changes to <yellow>bosses/" + bossId + ".yml",
                "<gray>and reload."), e -> save((Player) e.getWhoClicked()));

        set(32, icon(Material.SPAWNER, "<gold>Spawn now",
                "<gray>Spawn this boss at your location."), e -> {
            Player player = (Player) e.getWhoClicked();
            BossDefinition def = plugin.registry().get(bossId);
            if (def != null) {
                if (def.isArmy()) {
                    plugin.armyManager().start(def, player.getLocation());
                } else {
                    plugin.bossManager().spawn(def, player.getLocation());
                }
                player.closeInventory();
            }
        });

        set(40, icon(Material.ARROW, "<yellow>Back"), e -> new BossListMenu(plugin).open((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack stat(Material material, String label, int value) {
        return icon(material, "<yellow>" + label + ": <white>" + value,
                "<gray>Left-click <green>+", "<gray>Right-click <red>-");
    }

    private void save(Player player) {
        File file = new File(plugin.getDataFolder(), "bosses/" + bossId + ".yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        yml.set("difficulty", difficulty.name());
        yml.set("stats.health", health);
        yml.set("stats.armor", armor);
        yml.set("stats.damage", damage);
        yml.set("spawn.weight", weight);
        yml.set("terrain.enabled", terrainEnabled);
        try {
            yml.save(file);
            plugin.reloadAll();
            player.sendMessage(Text.mm("<green>Saved <yellow>" + bossId + "<green> and reloaded."));
            new BossEditorMenu(plugin, bossId).open(player);
        } catch (IOException ex) {
            player.sendMessage(Text.mm("<red>Failed to save: " + ex.getMessage()));
        }
    }
}
