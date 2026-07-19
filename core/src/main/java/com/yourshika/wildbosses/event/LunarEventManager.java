package com.yourshika.wildbosses.event;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Night-time "lunar events" (Blood Moon, Crystal Moon, ...). While one is active in an Overworld:
 * ambient themed particles drift around each nearby player, every naturally-spawned hostile mob is
 * buffed, and bosses spawn more readily. The event begins on a fresh night (rolled once against a
 * configurable chance) and ends at daybreak - so sleeping through the night also ends it.
 *
 * <p>Note: the vanilla moon's colour can't be changed server-side without a resource pack, so the
 * "red / crystal moon" is conveyed through heavy coloured particles and a title, not the moon texture.</p>
 */
public final class LunarEventManager implements Listener {

    private final WildBossesPlugin plugin;
    private final Map<UUID, String> activeByWorld = new HashMap<>();   // world uid -> event type
    private final Map<UUID, Long> rolledNight = new HashMap<>();       // world uid -> day number rolled
    private BukkitTask task;

    public LunarEventManager(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activeByWorld.clear();
    }

    public boolean isActive(World world) {
        return world != null && activeByWorld.containsKey(world.getUID());
    }

    public boolean activeAnywhere() {
        return !activeByWorld.isEmpty();
    }

    /** Extra boss-spawn attempts to run per cycle while any lunar event is active. */
    public int bossExtraAttempts() {
        return activeAnywhere()
                ? Math.max(0, plugin.getConfig().getInt("lunar-events.boss-extra-attempts", 1))
                : 0;
    }

    // ---- loop -----------------------------------------------------------------------------

    private void tick() {
        boolean enabled = plugin.getConfig().getBoolean("lunar-events.enabled", true);
        double chance = plugin.getConfig().getDouble("lunar-events.chance", 0.12);
        List<String> types = plugin.getConfig().getStringList("lunar-events.types");
        if (types.isEmpty()) {
            types = List.of("bloodmoon", "crystalmoon");
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            long timeOfDay = world.getTime() % 24000;
            boolean night = timeOfDay >= 13000 && timeOfDay < 23000;
            UUID id = world.getUID();
            String active = activeByWorld.get(id);
            if (active != null) {
                if (!night) {
                    endEvent(world, active); // dawn (or someone slept) - the event fades
                } else {
                    ambient(world, active);
                }
                continue;
            }
            if (!enabled || !night || world.getPlayers().isEmpty()) {
                continue;
            }
            long dayNumber = Math.floorDiv(world.getFullTime(), 24000L);
            Long rolled = rolledNight.get(id);
            if (rolled != null && rolled == dayNumber) {
                continue; // already rolled for tonight
            }
            rolledNight.put(id, dayNumber);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                startEvent(world, types.get(ThreadLocalRandom.current().nextInt(types.size())));
            }
        }
    }

    private void startEvent(World world, String type) {
        activeByWorld.put(world.getUID(), type.toLowerCase(Locale.ROOT));
        Component title = Text.mm(titleFor(type));
        Component sub = Text.mm(subtitleFor(type));
        for (Player p : world.getPlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(title, sub));
            p.sendMessage(title);
            p.playSound(p.getLocation(), soundFor(type), 1.0f, 0.6f);
        }
    }

    private void endEvent(World world, String type) {
        activeByWorld.remove(world.getUID());
        Component msg = Text.mm("<gray>The " + prettyName(type) + " fades with the dawn.");
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }

    // ---- effects --------------------------------------------------------------------------

    private void ambient(World world, String type) {
        boolean blood = type.equals("bloodmoon");
        for (Player p : world.getPlayers()) {
            var base = p.getLocation();
            for (int i = 0; i < 24; i++) {
                double dx = rand(9), dy = ThreadLocalRandom.current().nextDouble(0, 7), dz = rand(9);
                var loc = base.clone().add(dx, dy, dz);
                if (blood) {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(170, 8, 8), 1.6f));
                } else {
                    if ((i & 1) == 0) {
                        world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0.001);
                    } else {
                        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.4f));
                    }
                }
            }
        }
    }

    /** Buff hostile mobs that spawn naturally during the event (WildBosses' own mobs are skipped). */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !isActive(monster.getWorld())) {
            return;
        }
        if (monster.getScoreboardTags().contains("wildbosses")) {
            return; // never double-buff our bosses / minions / adds
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        String type = activeByWorld.get(monster.getWorld().getUID());
        double healthMult = plugin.getConfig().getDouble("lunar-events.mob-health-multiplier", 1.6);
        int strength = plugin.getConfig().getInt("lunar-events.mob-strength", 1);
        int speed = plugin.getConfig().getInt("lunar-events.mob-speed", 0);
        AttributeInstance maxHp = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null && healthMult > 1.0) {
            maxHp.setBaseValue(maxHp.getBaseValue() * healthMult);
            monster.setHealth(maxHp.getBaseValue());
        }
        addInfinite(monster, PotionEffectType.STRENGTH, strength);
        if (speed > 0) {
            addInfinite(monster, PotionEffectType.SPEED, speed - 1);
        }
        // A crystal moon makes them eerily bright; a blood moon makes them burn with fury.
        if ("crystalmoon".equals(type)) {
            monster.setGlowing(true);
        } else {
            addInfinite(monster, PotionEffectType.RESISTANCE, 0);
        }
    }

    private static void addInfinite(LivingEntity e, PotionEffectType type, int amplifier) {
        if (amplifier < 0) {
            return;
        }
        e.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, false));
    }

    // ---- text -----------------------------------------------------------------------------

    private String titleFor(String type) {
        return switch (type) {
            case "crystalmoon" -> "<gradient:#67e8f9:#a5f3fc><bold>Crystal Moon</bold></gradient>";
            default -> "<gradient:#7f1d1d:#ef4444><bold>Blood Moon</bold></gradient>";
        };
    }

    private String subtitleFor(String type) {
        return type.equals("crystalmoon")
                ? "<aqua>The night hums with crystal light - the mobs grow strange and strong."
                : "<red>The mobs grow bold and bloodthirsty tonight.";
    }

    private String prettyName(String type) {
        return type.equals("crystalmoon") ? "<aqua>Crystal Moon</aqua>" : "<red>Blood Moon</red>";
    }

    private String soundFor(String type) {
        return type.equals("crystalmoon") ? "block.amethyst_block.chime" : "entity.wither.spawn";
    }

    private static double rand(double m) {
        return ThreadLocalRandom.current().nextDouble(-m, m);
    }
}
