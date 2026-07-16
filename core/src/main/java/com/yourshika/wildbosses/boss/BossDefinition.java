package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.difficulty.Difficulty;
import com.yourshika.wildbosses.skill.SkillDefinition;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * An immutable, fully-parsed boss definition (loaded from a {@code bosses/<id>.yml} file).
 */
public record BossDefinition(
        String id,
        String name,
        String title,
        Difficulty difficulty,
        EntityType baseEntity,
        BossStats stats,
        EquipmentSet equipment,
        BossBarSettings bossBar,
        SpawnRules spawn,
        List<PhaseDefinition> phases,
        List<SkillDefinition> skills,
        DropTable drops,
        TerrainSettings terrain,
        ArmyDefinition army,
        RandomEquipment randomEquipment,
        Set<String> immunities,
        EnrageTimer enrageTimer
) {

    public boolean immuneTo(String key) {
        return immunities.contains(key);
    }

    public boolean isArmy() {
        return army != null;
    }

    public boolean hasTerrain() {
        return terrain != null && terrain.enabled();
    }
}
