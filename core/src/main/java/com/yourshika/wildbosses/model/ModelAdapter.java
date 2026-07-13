package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.entity.LivingEntity;

/**
 * Abstraction over how a boss is visually rendered. Implemented by the in-core vanilla fallback and,
 * optionally, by the BetterModel module (loaded reflectively when the BetterModel plugin is present).
 */
public interface ModelAdapter {

    /** Human-readable adapter name (for logs / GUI). */
    String name();

    /** Whether this adapter can render custom models (false = vanilla fallback only). */
    boolean supportsModels();

    /**
     * Attach the boss' model to the entity.
     *
     * @return a handle, or {@link ModelHandle#NOOP} if nothing was attached
     */
    ModelHandle attach(LivingEntity entity, BossDefinition def);
}
