package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.entity.LivingEntity;

/**
 * Fallback adapter: relies on the base entity's own appearance (equipment, scale, custom name).
 * No true custom mesh, but fully functional without any optional plugin.
 */
public final class VanillaModelAdapter implements ModelAdapter {

    @Override
    public String name() {
        return "Vanilla";
    }

    @Override
    public boolean supportsModels() {
        return false;
    }

    @Override
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        return ModelHandle.NOOP;
    }
}
