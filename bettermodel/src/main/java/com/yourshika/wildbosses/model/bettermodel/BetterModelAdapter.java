package com.yourshika.wildbosses.model.bettermodel;

import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.model.ModelAdapter;
import com.yourshika.wildbosses.model.ModelHandle;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * BetterModel-backed model adapter. Loaded reflectively by {@code ModelManager} only when the
 * BetterModel plugin is present, so {@code :core} never depends on BetterModel at compile time.
 *
 * <p>Attaches a BlockBench model to the boss entity via an {@link EntityTracker} and drives its
 * animations (idle/attack/phase). If the named model is not loaded in BetterModel, it degrades to
 * the vanilla appearance (returns {@link ModelHandle#NOOP}).</p>
 */
public final class BetterModelAdapter implements ModelAdapter {

    private final Plugin plugin;

    // Constructor signature is required by ModelManager's reflective loader.
    public BetterModelAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "BetterModel";
    }

    @Override
    public boolean supportsModels() {
        return true;
    }

    @Override
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        // Use the explicit model name if set, otherwise a model named after the boss id.
        String key = (def.model() != null && !def.model().isBlank()) ? def.model() : def.id();
        Optional<ModelRenderer> renderer = BetterModel.model(key);
        if (renderer.isEmpty()) {
            // Only warn if a model was explicitly requested; a missing id-named model is normal.
            if (def.hasModel()) {
                String available;
                try {
                    available = String.join(", ", BetterModel.modelKeys());
                } catch (Throwable t) {
                    available = "?";
                }
                plugin.getLogger().warning("BetterModel model '" + key + "' for boss " + def.id()
                        + " is not loaded; using vanilla. Loaded models: [" + available + "]"
                        + " - set 'model:' in the boss file to one of these, or rename your .bbmodel.");
            }
            return ModelHandle.NOOP;
        }
        EntityTracker tracker = renderer.get().create(BukkitAdapter.adapt(entity));
        return new BetterModelHandle(tracker);
    }

    /** Wraps a BetterModel {@link EntityTracker} behind the neutral {@link ModelHandle} interface. */
    private static final class BetterModelHandle implements ModelHandle {

        private final EntityTracker tracker;

        private BetterModelHandle(EntityTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void playAnimation(String name, boolean loop) {
            try {
                tracker.animate(name);
            } catch (Throwable ignored) {
                // animation name may not exist in the model; ignore
            }
        }

        @Override
        public void stopAnimation(String name) {
            try {
                tracker.stopAnimation(name);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void setTint(int rgb) {
            // Tinting is model/bone-specific in BetterModel 3.x; left as a no-op for now.
        }

        @Override
        public void remove() {
            try {
                tracker.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
