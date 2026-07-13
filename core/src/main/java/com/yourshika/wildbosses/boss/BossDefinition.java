package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.difficulty.Difficulty;
import com.yourshika.wildbosses.skill.SkillDefinition;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

/**
 * An immutable, fully-parsed boss definition (loaded from a {@code bosses/<id>.yml} file).
 */
public record BossDefinition(
        String id,
        String name,
        String title,
        Difficulty difficulty,
        EntityType baseEntity,
        String model,
        Map<String, String> animations,
        BossStats stats,
        EquipmentSet equipment,
        BossBarSettings bossBar,
        SpawnRules spawn,
        List<PhaseDefinition> phases,
        List<SkillDefinition> skills,
        DropTable drops,
        TerrainSettings terrain,
        ArmyDefinition army
) {

    public boolean isArmy() {
        return army != null;
    }

    public boolean hasTerrain() {
        return terrain != null && terrain.enabled();
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }

    /** Animation name for a given state key (e.g. {@code idle}, {@code attack}, {@code phase2}), or null. */
    public String animation(String state) {
        return animations == null ? null : animations.get(state);
    }
}
