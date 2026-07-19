package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.boss.CommandReward;
import com.yourshika.wildbosses.boss.DropEntry;
import com.yourshika.wildbosses.boss.RawDrop;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only view of a single boss' drop table: item, rarity, chance and enchants. */
public final class BestiaryDropsMenu extends Menu {

    private final String bossId;

    public BestiaryDropsMenu(WildBossesPlugin plugin, String bossId) {
        super(plugin, 54, "<dark_gray>WildBosses <gray>- Drops");
        this.bossId = bossId;
    }

    @Override
    protected void build() {
        BossDefinition def = plugin.registry().get(bossId);
        if (def == null) {
            set(22, icon(Material.BARRIER, "<red>Unknown boss."), null);
            set(49, back(), e -> new BestiaryMenu(plugin).open((Player) e.getWhoClicked()));
            return;
        }
        int slot = 0;
        for (DropEntry e : def.drops().items()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, dropIcon(e), null);
        }
        for (RawDrop r : def.drops().rawDrops()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, rawIcon(r), null);
        }
        for (CommandReward cr : def.drops().commandRewards()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, petIcon(cr), null);
        }
        if (slot == 0) {
            set(22, icon(Material.BARRIER, "<gray>This boss has no drops."), null);
        }
        set(49, back(), e -> new BestiaryMenu(plugin).open((Player) e.getWhoClicked()));
        filler(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack back() {
        return icon(Material.ARROW, "<yellow>Back to bestiary");
    }

    private ItemStack dropIcon(DropEntry e) {
        Material mat = e.material() == null ? Material.PAPER : e.material();
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (e.name() != null && !e.name().isBlank()) {
                meta.displayName(Text.mm(plugin.messages().tr(e.name())).decoration(TextDecoration.ITALIC, false));
            }
            List<Component> lore = new ArrayList<>();
            add(lore, e.rarity().loreLine());
            add(lore, "<gray>Chance: <yellow>" + pct(e.chance()) + "%");
            for (String token : e.enchants()) {
                add(lore, "<dark_gray>• <aqua>" + prettyEnchant(token));
                applyEnchant(meta, token);
            }
            if (e.glow() || e.rarity().glow()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack rawIcon(RawDrop r) {
        ItemStack item = r.stack().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            add(lore, r.rarity().loreLine());
            add(lore, "<gray>Chance: <yellow>" + pct(r.chance()) + "%");
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack petIcon(CommandReward cr) {
        String label = cr.announce() != null && !cr.announce().isBlank()
                ? cr.announce().replaceAll("<[^>]+>", "").replaceAll("(?i)^.*mythical\\s*-\\s*", "")
                : "Pet reward";
        return icon(Material.LEAD, "<light_purple>" + label,
                "<gradient:#ff6bd6:#c86bff>✦ Mythical",
                "<gray>Chance: <yellow>" + pct(cr.chance()) + "%",
                "<dark_gray>Granted via command on kill");
    }

    private static void add(List<Component> lore, String mini) {
        lore.add(Text.mm(mini).decoration(TextDecoration.ITALIC, false));
    }

    private static int pct(double chance) {
        return (int) Math.round(chance * 100);
    }

    private static String prettyEnchant(String token) {
        String[] p = token.split(":");
        String name = Text.titleCase(p[0]);
        return p.length > 1 ? name + " " + p[1] : name;
    }

    private void applyEnchant(ItemMeta meta, String token) {
        String[] parts = token.split(":");
        try {
            Enchantment ench = Registry.ENCHANTMENT.get(
                    NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            int level = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            if (ench != null) {
                meta.addEnchant(ench, level, true);
            }
        } catch (Exception ignored) {
            // display-only preview; ignore bad tokens
        }
    }

}
