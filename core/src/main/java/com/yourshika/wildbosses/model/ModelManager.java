package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Selects how bosses are rendered: a full <b>BetterModel</b> BlockBench model (loaded reflectively;
 * module in :bettermodel) if present, otherwise the base entity's <b>vanilla</b> appearance.
 */
public final class ModelManager {

    private static final String BETTERMODEL_ADAPTER =
            "com.yourshika.wildbosses.model.bettermodel.BetterModelAdapter";

    private final Plugin plugin;
    private ModelAdapter betterModel;

    public ModelManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void select() {
        betterModel = null;
        if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            try {
                Class<?> clazz = Class.forName(BETTERMODEL_ADAPTER);
                betterModel = (ModelAdapter) clazz.getConstructor(Plugin.class).newInstance(plugin);
                plugin.getLogger().info("BetterModel detected - custom models enabled.");
            } catch (Throwable t) {
                plugin.getLogger().warning("BetterModel is installed but the adapter failed to load ("
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + ").");
            }
        } else {
            plugin.getLogger().info("BetterModel not installed - bosses use their vanilla appearance.");
        }
    }

    public boolean supportsModels() {
        return betterModel != null;
    }

    /** Attach a BetterModel model to the boss entity, or {@link ModelHandle#NOOP} for vanilla. */
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        if (betterModel != null) {
            return tryAttach(betterModel, entity, def);
        }
        return ModelHandle.NOOP;
    }

    private ModelHandle tryAttach(ModelAdapter adapter, LivingEntity entity, BossDefinition def) {
        try {
            ModelHandle handle = adapter.attach(entity, def);
            return handle == null ? ModelHandle.NOOP : handle;
        } catch (Throwable t) {
            plugin.getLogger().warning(adapter.name() + " model attach failed for boss " + def.id()
                    + " (" + t.getMessage() + ").");
            return ModelHandle.NOOP;
        }
    }
}
