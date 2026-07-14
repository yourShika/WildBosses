package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Selects how bosses are rendered, trying each source in order and falling back cleanly:
 * <ol>
 *   <li><b>BetterModel</b> — full custom BlockBench model (loaded reflectively; module in :bettermodel).</li>
 *   <li><b>Oraxen texture</b> — a custom texture/model deployed to Oraxen, shown via an ItemDisplay.</li>
 *   <li><b>Vanilla</b> — the base entity's own appearance.</li>
 * </ol>
 */
public final class ModelManager {

    private static final String BETTERMODEL_ADAPTER =
            "com.yourshika.wildbosses.model.bettermodel.BetterModelAdapter";

    private final Plugin plugin;
    private ModelAdapter betterModel;
    private OraxenItemAdapter oraxen;

    public ModelManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void select() {
        betterModel = null;
        oraxen = null;

        if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            try {
                Class<?> clazz = Class.forName(BETTERMODEL_ADAPTER);
                betterModel = (ModelAdapter) clazz.getConstructor(Plugin.class).newInstance(plugin);
                plugin.getLogger().info("BetterModel detected - custom models enabled.");
            } catch (Throwable t) {
                plugin.getLogger().warning("BetterModel is installed but the adapter failed to load ("
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + ").");
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            oraxen = new OraxenItemAdapter(plugin);
            if (oraxen.supportsModels()) {
                plugin.getLogger().info("Oraxen detected - custom textures via ItemDisplay enabled.");
            }
        }
        if (betterModel == null && (oraxen == null || !oraxen.supportsModels())) {
            plugin.getLogger().info("No model provider - bosses use their vanilla appearance.");
        }
    }

    public boolean supportsModels() {
        return betterModel != null || (oraxen != null && oraxen.supportsModels());
    }

    /** Attach a model/texture to the boss entity, or {@link ModelHandle#NOOP} for vanilla. */
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        if (betterModel != null) {
            ModelHandle handle = tryAttach(betterModel, entity, def);
            if (handle != ModelHandle.NOOP) {
                return handle;
            }
        }
        if (oraxen != null && oraxen.supportsModels()) {
            ModelHandle handle = tryAttach(oraxen, entity, def);
            if (handle != ModelHandle.NOOP) {
                return handle;
            }
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
