package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.difficulty.Difficulty;
import com.yourshika.wildbosses.skill.ConditionDefinition;
import com.yourshika.wildbosses.skill.SkillDefinition;
import com.yourshika.wildbosses.skill.TriggerType;
import com.yourshika.wildbosses.util.NumberRange;
import com.yourshika.wildbosses.util.Params;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parses a boss {@link ConfigurationSection} (the root of a {@code bosses/<id>.yml}) into an
 * immutable {@link BossDefinition}. Tolerant: unknown/invalid values fall back to defaults with
 * a logged warning rather than aborting the load.
 */
public final class BossLoader {

    private final Logger logger;

    public BossLoader(Logger logger) {
        this.logger = logger;
    }

    public BossDefinition load(String id, ConfigurationSection root) {
        String name = root.getString("name", "<white>" + id);
        String title = root.getString("title", null);
        Difficulty difficulty = Difficulty.fromString(root.getString("difficulty"), Difficulty.MEDIUM);
        EntityType baseEntity = parseEntityType(root.getString("base-entity", "ZOMBIE"), id);
        String model = root.getString("model", "");
        Map<String, String> animations = parseAnimations(root.getConfigurationSection("animations"));

        BossStats stats = parseStats(root.getConfigurationSection("stats"));
        EquipmentSet equipment = parseEquipment(root.getConfigurationSection("equipment"));
        BossBarSettings bossBar = parseBossBar(root.getConfigurationSection("bossbar"), id);
        SpawnRules spawn = parseSpawn(root.getConfigurationSection("spawn"));
        List<PhaseDefinition> phases = parsePhases(root.getMapList("phases"));
        List<SkillDefinition> skills = parseSkills(root.getMapList("skills"), id);
        DropTable drops = parseDrops(root.getConfigurationSection("drops"));
        TerrainSettings terrain = parseTerrain(root.getConfigurationSection("terrain"), id);
        ArmyDefinition army = parseArmy(root.getConfigurationSection("army"));

        return new BossDefinition(id, name, title, difficulty, baseEntity, model, animations, stats,
                equipment, bossBar, spawn, phases, skills, drops, terrain, army);
    }

    // ---- component parsers ----------------------------------------------------------------

    private BossStats parseStats(ConfigurationSection s) {
        if (s == null) {
            return BossStats.defaults();
        }
        return new BossStats(
                s.getDouble("health", 200),
                s.getDouble("armor", 0),
                s.getDouble("armor-toughness", 0),
                s.getDouble("knockback-resistance", 0),
                s.getDouble("damage", 6),
                s.getDouble("speed", 0.25),
                s.getDouble("follow-range", 40),
                s.getDouble("scale", 1.0));
    }

    private EquipmentSet parseEquipment(ConfigurationSection s) {
        if (s == null) {
            return EquipmentSet.empty();
        }
        return new EquipmentSet(
                material(s.getString("hand")),
                material(s.getString("offhand")),
                material(s.getString("head")),
                material(s.getString("chest")),
                material(s.getString("legs")),
                material(s.getString("feet")));
    }

    private BossBarSettings parseBossBar(ConfigurationSection s, String id) {
        if (s == null) {
            return BossBarSettings.defaults();
        }
        BossBar.Color color = enumOrNull(BossBar.Color.class, s.getString("color"), "bossbar.color", id);
        BossBar.Overlay overlay = enumOr(BossBar.Overlay.class, s.getString("style"), BossBar.Overlay.PROGRESS,
                "bossbar.style", id);
        return new BossBarSettings(color, overlay);
    }

    private SpawnRules parseSpawn(ConfigurationSection s) {
        if (s == null) {
            return new SpawnRules(Set.of(World.Environment.NORMAL), 10, 40, -64, 320, 1800, 1);
        }
        Set<World.Environment> envs = parseEnvironments(s.getStringList("worlds"));
        return new SpawnRules(
                envs,
                s.getInt("weight", 10),
                s.getDouble("min-players-distance", 40),
                s.getInt("y.min", -64),
                s.getInt("y.max", 320),
                s.getInt("cooldown-seconds", 1800),
                s.getInt("max-concurrent", 1));
    }

    private List<PhaseDefinition> parsePhases(List<Map<?, ?>> raw) {
        List<PhaseDefinition> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Params p = new Params(toStringMap(m));
            out.add(new PhaseDefinition(
                    p.getDouble("at-health-percent", 100),
                    p.getBoolean("enrage", false),
                    p.getString("message", null),
                    p.getString("animation", null)));
        }
        out.sort((a, b) -> Double.compare(b.atHealthPercent(), a.atHealthPercent()));
        return out;
    }

    private List<SkillDefinition> parseSkills(List<Map<?, ?>> raw, String id) {
        List<SkillDefinition> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Map<String, Object> flat = toStringMap(m);
            Params all = new Params(flat);
            String mechanic = all.getString("mechanic", null);
            if (mechanic == null || mechanic.isBlank()) {
                logger.warning("[" + id + "] skill without a mechanic, skipping.");
                continue;
            }
            TriggerType trigger = TriggerType.fromString(all.getString("trigger", null), TriggerType.ON_TIMER);
            String targeter = all.getString("targeter", "self");
            Params mparams = new Params(toStringMap(flat.get("params")));
            List<ConditionDefinition> conditions = parseConditions(flat.get("conditions"));
            int cooldown = all.getInt("cooldown", 0);
            out.add(new SkillDefinition(trigger, all, mechanic.trim().toLowerCase(Locale.ROOT),
                    targeter.trim().toLowerCase(Locale.ROOT), mparams, conditions, cooldown));
        }
        return out;
    }

    private List<ConditionDefinition> parseConditions(Object raw) {
        List<ConditionDefinition> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object e : list) {
                Map<String, Object> m = toStringMap(e);
                String type = String.valueOf(m.getOrDefault("type", "")).trim().toLowerCase(Locale.ROOT);
                if (!type.isBlank()) {
                    out.add(new ConditionDefinition(type, new Params(m)));
                }
            }
        }
        return out;
    }

    private DropTable parseDrops(ConfigurationSection s) {
        if (s == null) {
            return DropTable.empty();
        }
        List<DropEntry> items = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("items")) {
            Params p = new Params(toStringMap(m));
            Material mat = material(p.getString("item", null));
            if (mat == null) {
                logger.warning("Drop entry with unknown item: " + p.getString("item", "null"));
                continue;
            }
            items.add(new DropEntry(
                    mat,
                    p.getRange("amount", new NumberRange(1, 1)),
                    clamp01(p.getDouble("chance", 1.0)),
                    p.getString("name", null),
                    p.getStringList("lore"),
                    p.getStringList("enchants"),
                    p.getInt("custom-model-data", -1),
                    p.getBoolean("glow", false)));
        }
        return new DropTable(items, s.getInt("xp", 0), s.getStringList("commands"));
    }

    private TerrainSettings parseTerrain(ConfigurationSection s, String id) {
        if (s == null) {
            return TerrainSettings.disabled();
        }
        boolean enabled = s.getBoolean("enabled", false);
        Map<Material, Material> mappings = new EnumMap<>(Material.class);
        ConfigurationSection mapSec = s.getConfigurationSection("mappings");
        if (mapSec != null) {
            for (String key : mapSec.getKeys(false)) {
                Material from = material(key);
                Material to = material(mapSec.getString(key));
                if (from == null || to == null) {
                    logger.warning("[" + id + "] invalid terrain mapping: " + key + " -> " + mapSec.getString(key));
                    continue;
                }
                mappings.put(from, to);
            }
        }
        return new TerrainSettings(
                enabled,
                Math.max(0, s.getInt("radius", 6)),
                Math.max(0, s.getInt("max-blocks", 4000)),
                s.getBoolean("restore-on-end", true),
                s.getBoolean("require-coreprotect", false),
                s.getBoolean("only-ungenerated-chunks", true),
                mappings);
    }

    private ArmyDefinition parseArmy(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        List<ArmyMinion> minions = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("minions")) {
            Params p = new Params(toStringMap(m));
            EntityType type = parseEntityType(p.getString("type", "ZOMBIE"), "army");
            minions.add(new ArmyMinion(
                    type,
                    Math.max(1, p.getInt("weight", 1)),
                    p.getDouble("health", 0),
                    p.getString("name", null),
                    p.getStringList("effects")));
        }
        return new ArmyDefinition(
                minions,
                Math.max(1, s.getInt("kill-threshold", 30)),
                Math.max(1, s.getInt("wave-size", 6)),
                Math.max(1, s.getInt("max-alive", 20)),
                Math.max(20, s.getInt("reinforce-interval-ticks", 120)),
                Math.max(4, s.getDouble("radius", 12)),
                ArmyDefinition.Outcome.fromString(s.getString("outcome"), ArmyDefinition.Outcome.CLEARED),
                s.getString("end-boss", null),
                Math.max(0, s.getInt("timeout-seconds", 0)));
    }

    private Map<String, String> parseAnimations(ConfigurationSection s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                out.put(key, s.getString(key));
            }
        }
        return out;
    }

    // ---- helpers --------------------------------------------------------------------------

    private Set<World.Environment> parseEnvironments(List<String> tokens) {
        Set<World.Environment> envs = new LinkedHashSet<>();
        for (String t : tokens) {
            switch (t.trim().toUpperCase(Locale.ROOT)) {
                case "OVERWORLD", "NORMAL" -> envs.add(World.Environment.NORMAL);
                case "NETHER" -> envs.add(World.Environment.NETHER);
                case "THE_END", "END" -> envs.add(World.Environment.THE_END);
                default -> logger.warning("Unknown world/environment: " + t);
            }
        }
        if (envs.isEmpty()) {
            envs.add(World.Environment.NORMAL);
        }
        return envs;
    }

    private EntityType parseEntityType(String raw, String id) {
        if (raw == null) {
            return EntityType.ZOMBIE;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("[" + id + "] unknown base-entity '" + raw + "', defaulting to ZOMBIE.");
            return EntityType.ZOMBIE;
        }
    }

    private static Material material(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static Map<String, Object> toStringMap(Object o) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String raw, String field, String id) {
        return enumOr(type, raw, null, field, id);
    }

    private <E extends Enum<E>> E enumOr(Class<E> type, String raw, E fallback, String field, String id) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("[" + id + "] invalid value '" + raw + "' for " + field + ", using default.");
            return fallback;
        }
    }
}
