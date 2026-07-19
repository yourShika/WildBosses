package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.Rarity;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full drop editor for a boss: view and tune every drop's chance / rarity / announce, delete drops,
 * and add a new drop straight from the item in your hand (captured 1:1 with all enchants and NBT).
 * Field-authored drops live in {@code drops.items}; hand-captured drops live in {@code drops.raw-items}
 * as a base64 ItemStack, so nothing is lost.
 */
public final class DropEditorMenu extends Menu {

    private final String bossId;
    private final File file;
    private final YamlConfiguration yml;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private final List<Map<String, Object>> raw = new ArrayList<>();
    private final List<Map<String, Object>> pets = new ArrayList<>();

    public DropEditorMenu(WildBossesPlugin plugin, String bossId) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Drops: " + bossId);
        this.bossId = bossId;
        this.file = new File(plugin.getDataFolder(), "bosses/" + bossId + ".yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
        load("drops.items", items);
        load("drops.raw-items", raw);
        load("drops.command-rewards", pets);
    }

    private void load(String path, List<Map<String, Object>> into) {
        for (Map<?, ?> m : yml.getMapList(path)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            into.add(copy);
        }
    }

    @Override
    protected void build() {
        int slot = 0;
        // Field-authored drops.
        for (int i = 0; i < items.size() && slot < 45; i++, slot++) {
            set(slot, fieldIcon(items.get(i)), handler(items, "drops.items", i));
        }
        // Hand-captured 1:1 drops.
        for (int i = 0; i < raw.size() && slot < 45; i++, slot++) {
            set(slot, rawIcon(raw.get(i)), handler(raw, "drops.raw-items", i));
        }
        // Command rewards (pets etc.) - chance/delete editable; the command + announce text stay in YAML.
        for (int i = 0; i < pets.size() && slot < 45; i++, slot++) {
            set(slot, petIcon(pets.get(i)), petHandler(i));
        }
        if (items.isEmpty() && raw.isEmpty() && pets.isEmpty()) {
            set(22, icon(Material.BARRIER, "<red>No drops yet.", "<gray>Add one from your hand below."), null);
        }

        set(45, icon(Material.ARROW, "<yellow>Back"),
                e -> new BossEditorMenu(plugin, bossId).open((Player) e.getWhoClicked()));
        set(48, icon(Material.LIME_DYE, "<green><bold>Add item from hand",
                        "<gray>Captures the item you're holding <white>1:1",
                        "<gray>(name, enchants, NBT) as a new drop.",
                        "<gray>Defaults: <aqua>Rare<gray>, 50% chance, announce on."),
                e -> addFromHand((Player) e.getWhoClicked()));
        set(50, icon(Material.WRITABLE_BOOK, "<green>Changes save automatically",
                        "<gray>Every edit is written to",
                        "<yellow>bosses/" + bossId + ".yml <gray>and reloaded."),
                null);
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    // ---- icons ----------------------------------------------------------------------------

    private ItemStack fieldIcon(Map<String, Object> it) {
        Material mat = matOf(it.get("item"));
        String name = it.get("name") != null
                ? plugin.messages().tr(String.valueOf(it.get("name")))
                : "<white>" + Text.titleCase(mat.name());
        List<String> lore = new ArrayList<>();
        lore.add(rarityOf(it).loreLine());
        lore.add("<gray>Chance: <yellow>" + Math.round(toDouble(it.get("chance"), 1.0) * 100) + "%");
        lore.add("<gray>Announce: " + (announceOn(it) ? "<green>on" : "<red>off"));
        for (Object en : listOf(it.get("enchants"))) {
            lore.add("<dark_gray>• <aqua>" + prettyEnchant(String.valueOf(en)));
        }
        appendControls(lore);
        return icon(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack rawIcon(Map<String, Object> it) {
        ItemStack stack = decode(String.valueOf(it.get("data")));
        if (stack == null) {
            return icon(Material.BARRIER, "<red>Corrupt item data", "<gray>Delete this entry (drop key).");
        }
        ItemStack display = stack.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            add(lore, "<gray>» <white>captured 1:1 from hand");
            add(lore, rarityOf(it).loreLine());
            add(lore, "<gray>Chance: <yellow>" + Math.round(toDouble(it.get("chance"), 1.0) * 100) + "%");
            add(lore, "<gray>Announce: " + (announceOn(it) ? "<green>on" : "<red>off"));
            List<String> ctrl = new ArrayList<>();
            appendControls(ctrl);
            for (String c : ctrl) {
                add(lore, c);
            }
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private void appendControls(List<String> lore) {
        lore.add(" ");
        lore.add("<gray>Left-click <aqua>edit chance (precise)");
        lore.add("<gray>Right-click <aqua>toggle announce");
        lore.add("<gray>Middle-click <light_purple>cycle rarity");
        lore.add("<gray>Drop key (Q) <red>delete");
    }

    // ---- interaction ----------------------------------------------------------------------

    private java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> handler(
            List<Map<String, Object>> list, String section, int index) {
        return e -> {
            if (index >= list.size()) {
                return;
            }
            ClickType click = e.getClick();
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                list.remove(index);
                autoSave();
                rebuild();
            } else if (click == ClickType.MIDDLE) {
                Map<String, Object> it = list.get(index);
                Rarity[] all = Rarity.values();
                it.put("rarity", all[(rarityOf(it).ordinal() + 1) % all.length].name());
                autoSave();
                rebuild();
            } else if (e.isRightClick()) {
                Map<String, Object> it = list.get(index);
                it.put("announce", !announceOn(it));
                autoSave();
                rebuild();
            } else {
                // Left-click opens the precise chance editor for this drop.
                autoSave();
                new ChanceEditorMenu(plugin, bossId, section, index).open((Player) e.getWhoClicked());
            }
        };
    }

    private ItemStack petIcon(Map<String, Object> it) {
        String cmd = it.get("command") == null ? "?" : String.valueOf(it.get("command"));
        return icon(Material.LEAD, "<light_purple>Command reward",
                "<gradient:#ff6bd6:#c86bff>✦ Mythical",
                "<gray>Command: <white>" + cmd,
                "<gray>Chance: <yellow>" + Math.round(toDouble(it.get("chance"), 1.0) * 100) + "%",
                " ",
                "<gray>Left-click <aqua>edit chance (precise)",
                "<gray>Drop key (Q) <red>delete",
                "<dark_gray>(command + announce text: edit in the YAML)");
    }

    private java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> petHandler(int index) {
        return e -> {
            if (index >= pets.size()) {
                return;
            }
            ClickType click = e.getClick();
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                pets.remove(index);
                autoSave();
                rebuild();
            } else {
                autoSave();
                new ChanceEditorMenu(plugin, bossId, "drops.command-rewards", index)
                        .open((Player) e.getWhoClicked());
            }
        };
    }

    private void addFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(Text.mm("<red>Hold the item you want to add in your main hand first."));
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("data", Base64.getEncoder().encodeToString(hand.serializeAsBytes()));
        entry.put("chance", 0.5);
        entry.put("rarity", "RARE");
        entry.put("announce", true);
        raw.add(entry);
        player.sendMessage(Text.mm("<green>Added <white>" + Text.plain(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(hand.effectiveName())) + "<green> as a drop."));
        autoSave();
        rebuild();
    }

    /** Persist the current drops to the boss file and hot-reload, silently (the editor auto-saves). */
    private void autoSave() {
        yml.set("drops.items", items.isEmpty() ? null : items);
        yml.set("drops.raw-items", raw.isEmpty() ? null : raw);
        yml.set("drops.command-rewards", pets.isEmpty() ? null : pets);
        try {
            yml.save(file);
            plugin.reloadAll();
        } catch (IOException ex) {
            plugin.getLogger().warning("Drop auto-save failed for " + bossId + ": " + ex.getMessage());
        }
    }

    // ---- helpers --------------------------------------------------------------------------

    /** Drops announce by default; only an explicit {@code announce: false} silences one. */
    private static boolean announceOn(Map<String, Object> it) {
        return !Boolean.FALSE.equals(it.get("announce"));
    }

    private static void add(List<Component> lore, String mini) {
        lore.add(Text.mm(mini).decoration(TextDecoration.ITALIC, false));
    }

    private static Rarity rarityOf(Map<String, Object> it) {
        return Rarity.fromString(it.get("rarity") == null ? null : String.valueOf(it.get("rarity")), Rarity.COMMON);
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

    private static List<?> listOf(Object raw) {
        return raw instanceof List<?> l ? l : List.of();
    }

    private static String prettyEnchant(String token) {
        String[] p = token.split(":");
        String name = Text.titleCase(p[0]);
        return p.length > 1 ? name + " " + p[1] : name;
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
