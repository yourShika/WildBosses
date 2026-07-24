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
 * Sets how many of an item a drop yields, opened from {@link DropEditorMenu} (shift-click). Field
 * drops ({@code drops.items}) get a MIN and a MAX row (a random count in that range drops each time,
 * e.g. arrows 2-20); hand-captured drops ({@code drops.raw-items}) get a single fixed count. Values
 * are clamped to 1-64 and written back to {@code bosses/<id>.yml} live, exactly like the chance editor.
 */
public final class AmountEditorMenu extends Menu {

    private final String bossId;
    private final String section;
    private final int index;
    private final boolean rangeMode;
    private final File file;
    private final YamlConfiguration yml;
    private final List<Map<String, Object>> list = new ArrayList<>();

    public AmountEditorMenu(WildBossesPlugin plugin, String bossId, String section, int index) {
        super(plugin, 27, "<dark_gray>WildBosses <gray>- Amount");
        this.bossId = bossId;
        this.section = section;
        this.index = index;
        this.rangeMode = section.equals("drops.items");
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

        int[] amt = amount();

        if (rangeMode) {
            // Row 0: minimum. Row 1: maximum.
            row(1, "<green>" + tr("Min"), amt[0], true);
            row(10, "<gold>" + tr("Max"), amt[1], false);
            set(22, icon(Material.PAPER, "<gold><bold>" + amt[0] + "-" + amt[1] + "<gray> per drop",
                    "<gray>Drop: <white>" + label(),
                    " ",
                    "<gray>A random count in this range",
                    "<gray>drops each time (e.g. arrows 2-20)."), null);
        } else {
            row(10, "<aqua>" + tr("Amount"), amt[0], true); // captured stacks are a single fixed count
            set(22, icon(Material.PAPER, "<gold><bold>" + amt[0] + "<gray> per drop",
                    "<gray>Drop: <white>" + label(),
                    " ",
                    "<gray>Hand-captured drops use a",
                    "<gray>single fixed amount."), null);
        }

        set(18, icon(Material.ARROW, "<yellow>Back to drops"), e -> back((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    /** One adjustable row: -10 -5 -1 [label:value] +1 +5 +10, starting at {@code base} slot. */
    private void row(int base, String label, int value, boolean isMinOrSingle) {
        step(base, "-10", Material.RED_STAINED_GLASS, -10, isMinOrSingle);
        step(base + 1, "-5", Material.RED_STAINED_GLASS, -5, isMinOrSingle);
        step(base + 2, "-1", Material.PINK_STAINED_GLASS, -1, isMinOrSingle);
        set(base + 3, icon(Material.PAPER, label + " <white>" + value), null);
        step(base + 4, "+1", Material.LIME_STAINED_GLASS, 1, isMinOrSingle);
        step(base + 5, "+5", Material.GREEN_STAINED_GLASS, 5, isMinOrSingle);
        step(base + 6, "+10", Material.GREEN_STAINED_GLASS, 10, isMinOrSingle);
    }

    private void step(int slot, String name, Material mat, int delta, boolean minSide) {
        ItemStack item = icon(mat, (delta >= 0 ? "<green>" : "<red>") + name);
        set(slot, item, e -> {
            adjust(delta, minSide);
            rebuild();
        });
    }

    // ---- data -----------------------------------------------------------------------------

    private void adjust(int delta, boolean minSide) {
        int[] a = amount();
        if (!rangeMode) {
            int v = clamp(a[0] + delta);
            writeSingle(v);
            return;
        }
        if (minSide) {
            a[0] = clamp(a[0] + delta);
            if (a[1] < a[0]) {
                a[1] = a[0]; // keep max >= min
            }
        } else {
            a[1] = clamp(a[1] + delta);
            if (a[0] > a[1]) {
                a[0] = a[1]; // keep min <= max
            }
        }
        writeRange(a[0], a[1]);
    }

    private void writeRange(int min, int max) {
        list.get(index).put("amount", min == max ? Integer.valueOf(min) : (min + "-" + max));
        save();
    }

    private void writeSingle(int count) {
        Map<String, Object> it = list.get(index);
        ItemStack stack = decode(String.valueOf(it.get("data")));
        if (stack != null) {
            stack.setAmount(count);
            it.put("data", Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        } else {
            it.put("amount", count);
        }
        save();
    }

    private void save() {
        yml.set(section, list);
        try {
            yml.save(file);
            plugin.reloadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("Amount save failed for " + bossId + ": " + ex.getMessage());
        }
    }

    /** Current {min, max} for this drop, clamped to 1-64. */
    private int[] amount() {
        Map<String, Object> it = list.get(index);
        if (!rangeMode) {
            ItemStack stack = decode(String.valueOf(it.get("data")));
            int v = clamp(stack != null ? stack.getAmount() : 1);
            return new int[]{v, v};
        }
        Object raw = it.get("amount");
        if (raw instanceof Number n) {
            int v = clamp(n.intValue());
            return new int[]{v, v};
        }
        String s = raw == null ? "1" : String.valueOf(raw).trim();
        if (s.contains("-")) {
            String[] p = s.split("-", 2);
            try {
                int a = clamp(Integer.parseInt(p[0].trim()));
                int b = clamp(Integer.parseInt(p[1].trim()));
                return new int[]{Math.min(a, b), Math.max(a, b)};
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        try {
            int v = clamp(Integer.parseInt(s));
            return new int[]{v, v};
        } catch (NumberFormatException ex) {
            return new int[]{1, 1};
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
        ItemStack stack = decode(String.valueOf(it.get("data")));
        if (stack != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(stack.effectiveName());
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

    private static int clamp(int v) {
        return Math.max(1, Math.min(64, v));
    }
}
