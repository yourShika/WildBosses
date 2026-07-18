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

        var contributors = boss.damageByPlayer();
        if (plugin.config().participationLoot() && !contributors.isEmpty()) {
            // Everyone who dealt damage gets their own loot roll at their feet (no loot stealing).
            for (java.util.UUID id : contributors.keySet()) {
                Player p = Bukkit.getPlayer(id);
                Location dropLoc = (p != null && p.getWorld().equals(world)) ? p.getLocation() : loc;
                rollDrops(drops, dropLoc, boss, p);
            }
        } else {
            rollDrops(drops, loc, boss, killer);
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

    private void rollDrops(DropTable drops, Location loc, ActiveBoss boss, Player finder) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        // Build the candidate pool (item + raw drops) and roll each one's chance independently.
        List<Candidate> winners = new ArrayList<>();
        List<Candidate> losers = new ArrayList<>();
        for (DropEntry entry : drops.items()) {
            ItemStack stack = build(entry);
            Component base = (entry.name() != null && !entry.name().isBlank())
                    ? Text.mm(entry.name()).decoration(TextDecoration.ITALIC, false)
                    : stack.effectiveName();
            (ThreadLocalRandom.current().nextDouble() < entry.chance() ? winners : losers)
                    .add(new Candidate(entry.chance(), stack, entry.rarity(), base));
        }
        for (com.yourshika.wildbosses.boss.RawDrop raw : drops.rawDrops()) {
            ItemStack stack = raw.stack().clone();
            (ThreadLocalRandom.current().nextDouble() < raw.chance() ? winners : losers)
                    .add(new Candidate(raw.chance(), stack, raw.rarity(), stack.effectiveName()));
        }

        int max = plugin.config().maxDrops();
        int min = plugin.config().minDrops();
        // Too many rolled? Keep only the rarest ones (highest rarity, then lowest chance).
        if (winners.size() > max) {
            winners.sort((a, b) -> {
                int r = Integer.compare(b.rarity.ordinal(), a.rarity.ordinal());
                return r != 0 ? r : Double.compare(a.weight, b.weight);
            });
            winners = new ArrayList<>(winners.subList(0, max));
        }
        // Too few? Top up to the minimum from the ones that missed (weighted by their chance).
        while (winners.size() < min && !losers.isEmpty()) {
            Candidate c = pickWeighted(losers);
            if (c == null) {
                break;
            }
            losers.remove(c);
            winners.add(c);
        }

        for (Candidate c : winners) {
            org.bukkit.entity.Item dropped = world.dropItemNaturally(loc.clone().add(0, 0.5, 0), c.stack);
            dropped.setGlowing(true);   // only a few drop now, so make them all easy to see
            dropped.setWillAge(false);
            announceItem(boss, c.base, c.stack, c.rarity, finder); // every dropped item is announced
        }
        rollCommandRewards(drops, boss, finder);
    }

    private static Candidate pickWeighted(List<Candidate> pool) {
        double total = 0;
        for (Candidate c : pool) {
            total += Math.max(0, c.weight);
        }
        if (total <= 0) {
            return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double acc = 0;
        for (Candidate c : pool) {
            acc += Math.max(0, c.weight);
            if (r < acc) {
                return c;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** A rolled drop candidate (item + its rarity, chance-weight and broadcast name). */
    private static final class Candidate {
        final double weight;
        final ItemStack stack;
        final com.yourshika.wildbosses.boss.Rarity rarity;
        final Component base;

        Candidate(double weight, ItemStack stack, com.yourshika.wildbosses.boss.Rarity rarity, Component base) {
            this.weight = weight;
            this.stack = stack;
            this.rarity = rarity;
            this.base = base;
        }
    }

    /** Roll chance-based console rewards (e.g. granting a pet), one roll per finder. */
    private void rollCommandRewards(DropTable drops, ActiveBoss boss, Player finder) {
        if (finder == null || drops.commandRewards().isEmpty()) {
            return;
        }
        for (com.yourshika.wildbosses.boss.CommandReward cr : drops.commandRewards()) {
            if (ThreadLocalRandom.current().nextDouble() > cr.chance()) {
                continue;
            }
            String cmd = cr.command().replace("%player%", finder.getName()).replace("%boss%", boss.def().id());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (cr.announce() != null && !cr.announce().isBlank() && plugin.config().dropBroadcastEnabled()) {
                plugin.broadcaster().bossDrop(boss.def(), Text.mm(cr.announce()), 1, finder.getName());
            }
        }
    }

    /** Broadcast a notable drop, tagged with its rarity and a hoverable item preview. */
    private void announceItem(ActiveBoss boss, Component base, ItemStack stack,
                              com.yourshika.wildbosses.boss.Rarity rarity, Player finder) {
        if (!plugin.config().dropBroadcastEnabled()) {
            return;
        }
        Component display = Text.mm(rarity.inline())
                .append(Component.text(" "))
                .append(Component.text("["))
                .append(base)
                .append(Component.text("]"))
                .append(stack.getAmount() > 1
                        ? Text.mm(" <gray>×" + stack.getAmount())
                        : Component.empty())
                .hoverEvent(stack);
        plugin.broadcaster().bossDrop(boss.def(), display, stack.getAmount(),
                finder == null ? null : finder.getName());
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
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mm(entry.rarity().loreLine()).decoration(TextDecoration.ITALIC, false));
        for (String line : entry.lore()) {
            lore.add(Text.mm(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        for (String token : entry.enchants()) {
            applyEnchant(meta, token);
        }
        if (entry.glow() || entry.rarity().glow()) {
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
