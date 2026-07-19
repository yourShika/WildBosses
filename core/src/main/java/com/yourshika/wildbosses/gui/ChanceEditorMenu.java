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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A focused editor for a single drop's chance, opened from {@link DropEditorMenu}. Offers fine steps
 * (±1/5/10%) and quick presets, writing back to {@code bosses/<id>.yml} and reloading on every click.
 * The drop is addressed by its YAML section ({@code drops.items} / {@code drops.raw-items} /
 * {@code drops.command-rewards}) and index within that section.
 */
public final class ChanceEditorMenu extends Menu {

    private static final int[] PRESETS = {1, 5, 10, 25, 50, 75, 100};

    private final String bossId;
    private final String section;
    private final int index;
    private final File file;
    private final YamlConfiguration yml;
    private final List<Map<String, Object>> list = new ArrayList<>();

    public ChanceEditorMenu(WildBossesPlugin plugin, String bossId, String section, int index) {
        super(plugin, 27, "<dark_gray>WildBosses <gray>- Chance");
        this.bossId = bossId;
        this.section = section;
        this.index = index;
        this.file = new File(plugin.getDataFolder(), "bosses/" + bossId + ".yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : yml.getMapList(section)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : m.entrySet()) {
                copy.put(String.valueOf(en.getKey()), en.getValue());
            }
            list.add(copy);
        }
    }

    @Override
    protected void build() {
        if (index < 0 || index >= list.size()) {
            set(13, icon(Material.BARRIER, "<red>This drop no longer exists."), null);
            set(18, icon(Material.ARROW, "<yellow>Back"), e -> back((Player) e.getWhoClicked()));
            filler(Material.BLACK_STAINED_GLASS_PANE);
            return;
        }

        int pct = (int) Math.round(chance() * 100);

        // Row 1: coarse decreases | current value | coarse increases.
        step(1, "-10%", Material.RED_STAINED_GLASS, -0.10);
        step(2, "-5%", Material.RED_STAINED_GLASS, -0.05);
        step(3, "-1%", Material.PINK_STAINED_GLASS, -0.01);
        set(4, icon(Material.PAPER, "<gold><bold>" + pct + "%<gray> chance",
                "<gray>Drop: <white>" + label(),
                " ",
                "<gray>Use the buttons to fine-tune,",
                "<gray>or snap to a preset below."), null);
        step(5, "+1%", Material.LIME_STAINED_GLASS, 0.01);
        step(6, "+5%", Material.GREEN_STAINED_GLASS, 0.05);
        step(7, "+10%", Material.GREEN_STAINED_GLASS, 0.10);

        // Row 2: quick presets.
        for (int i = 0; i < PRESETS.length; i++) {
            preset(10 + i, PRESETS[i]);
        }

        set(18, icon(Material.ARROW, "<yellow>Back to drops"), e -> back((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private void step(int slot, String name, Material mat, double delta) {
        ItemStack item = icon(mat, (delta >= 0 ? "<green>" : "<red>") + name,
                "<gray>Set chance to <yellow>" + (int) Math.round(clamp01(chance() + delta) * 100) + "%");
        set(slot, item, e -> {
            setChance(clamp01(chance() + delta));
            rebuild();
        });
    }

    private void preset(int slot, int percent) {
        ItemStack item = icon(Material.MAP, "<aqua>Set " + percent + "%",
                "<gray>Snap chance to <yellow>" + percent + "%");
        set(slot, item, e -> {
            setChance(clamp01(percent / 100.0));
            rebuild();
        });
    }

    // ---- data -----------------------------------------------------------------------------

    private double chance() {
        Object raw = list.get(index).get("chance");
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return raw == null ? 1.0 : Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    private void setChance(double value) {
        list.get(index).put("chance", round2(value));
        yml.set(section, list);
        try {
            yml.save(file);
            plugin.reloadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("Chance save failed for " + bossId + ": " + ex.getMessage());
        }
    }

    private String label() {
        Map<String, Object> it = list.get(index);
        if (it.get("name") != null) {
            return Text.plain(plugin.messages().tr(String.valueOf(it.get("name"))));
        }
        if (it.get("item") != null) {
            return Text.titleCase(String.valueOf(it.get("item")));
        }
        if (it.get("command") != null) {
            return "Command reward";
        }
        if (it.get("data") != null) {
            ItemStack stack = decode(String.valueOf(it.get("data")));
            if (stack != null) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(stack.effectiveName());
            }
        }
        return "drop";
    }

    private void back(Player player) {
        new DropEditorMenu(plugin, bossId).open(player);
    }

    private static ItemStack decode(String data) {
        if (data == null || data.isBlank() || "null".equals(data)) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (Exception ex) {
            return null;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
