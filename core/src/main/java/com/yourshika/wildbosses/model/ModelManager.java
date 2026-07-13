package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Selects and holds the active {@link ModelAdapter}. If the BetterModel plugin is present, the
 * optional {@code BetterModelAdapter} (in the {@code :bettermodel} module) is loaded reflectively;
 * otherwise the vanilla fallback is used. Loading reflectively keeps {@code :core} free of any
 * compile-time dependency on BetterModel.
 */
public final class ModelManager {

    private static final String BETTERMODEL_ADAPTER =
            "com.yourshika.wildbosses.model.bettermodel.BetterModelAdapter";

    private final Plugin plugin;
    private ModelAdapter adapter = new VanillaModelAdapter();

    public ModelManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void select() {
        if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            try {
                Class<?> clazz = Class.forName(BETTERMODEL_ADAPTER);
                adapter = (ModelAdapter) clazz.getConstructor(Plugin.class).newInstance(plugin);
                plugin.getLogger().info("BetterModel detected - custom models enabled via " + adapter.name() + ".");
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("BetterModel is installed but the adapter failed to load ("
                        + t.getClass().getSimpleName() + ": " + t.getMessage() + "); using the vanilla fallback.");
            }
        } else {
            plugin.getLogger().info("BetterModel not installed - using the vanilla model fallback.");
        }
        adapter = new VanillaModelAdapter();
    }

    public ModelAdapter adapter() {
        return adapter;
    }

    public boolean supportsModels() {
        return adapter.supportsModels();
    }

    /**
     * Attach the boss' model. The adapter resolves the model name as {@code model:} if set, otherwise
     * the boss id (so a model whose name matches the boss id is used automatically). Returns
     * {@link ModelHandle#NOOP} when no matching model is loaded (vanilla appearance).
     */
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        if (!adapter.supportsModels()) {
            return ModelHandle.NOOP;
        }
        try {
            ModelHandle handle = adapter.attach(entity, def);
            return handle == null ? ModelHandle.NOOP : handle;
        } catch (Throwable t) {
            plugin.getLogger().warning("Model attach failed for boss " + def.id()
                    + " (" + t.getMessage() + "); using vanilla appearance.");
            return ModelHandle.NOOP;
        }
    }
}
