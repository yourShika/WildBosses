package com.yourshika.wildbosses.gui;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal chest-menu framework: an {@link InventoryHolder} that maps slots to click handlers.
 * Clicks are cancelled and dispatched by {@link GuiListener}.
 */
public abstract class Menu implements InventoryHolder {

    protected final WildBossesPlugin plugin;
    protected final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(WildBossesPlugin plugin, int size, String miniTitle) {
        this.plugin = plugin;
        // Titles and all icon text are auto-translated via the active language's `terms` map.
        this.inventory = Bukkit.createInventory(this, size, Text.mm(plugin.messages().tr(miniTitle)));
    }

    /** Populate items/actions. Called on open and on {@link #rebuild()}. */
    protected abstract void build();

    /** Translate an authored label/word via the active language's {@code terms} map. */
    protected String tr(String mini) {
        return plugin.messages().tr(mini);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void rebuild() {
        actions.clear();
        inventory.clear();
        build();
    }

    public void open(Player player) {
        rebuild();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ---- item helpers ---------------------------------------------------------------------

    /**
     * Build a menu icon. The name and every lore line are run through the active language's
     * {@code terms} map, so static GUI labels ("Back", "Close", control hints, ...) localise
     * automatically wherever a translation exists (and stay as authored otherwise).
     */
    protected ItemStack icon(Material material, String miniName, String... miniLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(plugin.messages().tr(miniName)).decoration(TextDecoration.ITALIC, false));
            if (miniLore.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String line : miniLore) {
                    lore.add(Text.mm(plugin.messages().tr(line)).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected void filler(Material material) {
        ItemStack pane = icon(material, "<gray>");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }
}
