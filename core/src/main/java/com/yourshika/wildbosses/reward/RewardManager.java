package com.yourshika.wildbosses.reward;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.BossDeathListener;
import com.yourshika.wildbosses.boss.DropEntry;
import com.yourshika.wildbosses.boss.DropTable;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Grants a boss' configured rewards on death: rolled item drops (with MiniMessage names/lore,
 * enchants, glint, model data), experience, and console reward commands.
 */
public final class RewardManager implements BossDeathListener {

    private final WildBossesPlugin plugin;

    public RewardManager(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onBossDeath(ActiveBoss boss, Player killer, EntityDeathEvent event) {
        DropTable drops = boss.def().drops();
        Location loc = boss.location();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        for (DropEntry entry : drops.items()) {
            if (ThreadLocalRandom.current().nextDouble() > entry.chance()) {
                continue;
            }
            world.dropItemNaturally(loc, build(entry));
        }

        if (drops.xp() > 0) {
            ExperienceOrb orb = world.spawn(loc, ExperienceOrb.class);
            orb.setExperience(drops.xp());
        }

        String playerName = killer != null ? killer.getName() : "";
        for (String command : drops.commands()) {
            String resolved = command.replace("%player%", playerName).replace("%boss%", boss.def().id());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private ItemStack build(DropEntry entry) {
        ItemStack item = new ItemStack(entry.material(), Math.max(1, entry.amount().roll()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (entry.name() != null && !entry.name().isBlank()) {
            meta.displayName(Text.mm(entry.name()).decoration(TextDecoration.ITALIC, false));
        }
        if (!entry.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : entry.lore()) {
                lore.add(Text.mm(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        for (String token : entry.enchants()) {
            applyEnchant(meta, token);
        }
        if (entry.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (entry.customModelData() >= 0) {
            meta.setCustomModelData(entry.customModelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void applyEnchant(ItemMeta meta, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String[] parts = token.split(":");
        Enchantment enchant = enchantment(parts[0]);
        if (enchant == null) {
            plugin.getLogger().warning("Unknown enchantment in drop: " + parts[0]);
            return;
        }
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        }
        meta.addEnchant(enchant, level, true);
    }

    private Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
        } catch (Exception e) {
            return null;
        }
    }
}
