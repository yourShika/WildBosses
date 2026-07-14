package com.yourshika.wildbosses.model;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * Renders a boss from an Oraxen item ({@code wildbosses_<bossId>}, created by {@link
 * com.yourshika.wildbosses.integration.OraxenAssets}) using an {@link ItemDisplay} mounted on an
 * invisible base mob. This is the "custom texture without BetterModel" path.
 *
 * <p>Experimental: uses the Oraxen API reflectively and depends on the Oraxen pack being deployed and
 * reloaded. If anything is missing it degrades to the vanilla appearance.</p>
 */
public final class OraxenItemAdapter implements ModelAdapter {

    private final Plugin plugin;
    private boolean available;
    private Method getItemById;
    private Method builderBuild;

    public OraxenItemAdapter(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        if (Bukkit.getPluginManager().getPlugin("Oraxen") == null) {
            return;
        }
        try {
            Class<?> oraxenItems = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            getItemById = oraxenItems.getMethod("getItemById", String.class);
            available = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Oraxen present but its item API was not found ("
                    + t.getClass().getSimpleName() + "); custom textures via Oraxen disabled.");
        }
    }

    @Override
    public String name() {
        return "Oraxen";
    }

    @Override
    public boolean supportsModels() {
        return available;
    }

    @Override
    public ModelHandle attach(LivingEntity entity, BossDefinition def) {
        if (!available) {
            return ModelHandle.NOOP;
        }
        ItemStack item = buildItem("wildbosses_" + def.id());
        if (item == null) {
            return ModelHandle.NOOP; // no deployed texture/model for this boss
        }
        ItemDisplay display = entity.getWorld().spawn(entity.getLocation(), ItemDisplay.class);
        display.setItemStack(item);
        display.setBillboard(Display.Billboard.FIXED);
        float scale = (float) Math.max(1.0, def.stats().scale() * 2.0); // items render small; scale up
        display.setTransformation(new Transformation(
                new Vector3f(0, (float) (entity.getHeight() * 0.5), 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)));
        entity.setInvisible(true);
        entity.addPassenger(display);
        return new OraxenHandle(display);
    }

    private ItemStack buildItem(String id) {
        try {
            Object builder = getItemById.invoke(null, id);
            if (builder == null) {
                return null;
            }
            if (builderBuild == null) {
                builderBuild = builder.getClass().getMethod("build");
            }
            Object stack = builderBuild.invoke(builder);
            return stack instanceof ItemStack is ? is : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class OraxenHandle implements ModelHandle {

        private final ItemDisplay display;

        private OraxenHandle(ItemDisplay display) {
            this.display = display;
        }

        @Override
        public void playAnimation(String name, boolean loop) {
        }

        @Override
        public void stopAnimation(String name) {
        }

        @Override
        public void setTint(int rgb) {
        }

        @Override
        public void remove() {
            try {
                display.remove();
            } catch (Throwable ignored) {
            }
        }
    }
}
