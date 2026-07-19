package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state for a single spawned boss: its entity, boss bar, current phase and per-skill timers.
 * Also manages which nearby players see the boss bar.
 */
public final class ActiveBoss {

    /** Range (blocks) at which players are shown the boss bar. */
    private static final double BOSS_BAR_RANGE = 64.0;

    private final BossDefinition def;
    private final LivingEntity entity;
    private final BossBar bossBar;
    private double maxHealth;
    private final long spawnTick;
    private final String encounterId;

    private boolean engaged;
    private long scaleLockTick;
    private int scaledPlayers;
    private World ticketWorld;
    private int ticketCx;
    private int ticketCz;
    private boolean hasTicket;

    private final Map<Integer, Long> skillNextTick = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    private int phaseIndex = -1;
    private LivingEntity target;
    private boolean removed;

    private long fleeAtTick;

    private final Set<UUID> healers = new HashSet<>();
    private final Map<UUID, Double> damageByPlayer = new HashMap<>();
    private double addMultiplier = 1.0;
    private double healerHealPerTick;
    private long lastEnrageTick;
    private boolean deathHandled;
    private boolean scriptedTeleport;

    public ActiveBoss(BossDefinition def, LivingEntity entity, BossBar bossBar,
                      double maxHealth, long spawnTick, String encounterId) {
        this.def = def;
        this.entity = entity;
        this.bossBar = bossBar;
        this.maxHealth = maxHealth;
        this.spawnTick = spawnTick;
        this.encounterId = encounterId;
    }

    public BossDefinition def() {
        return def;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public long spawnTick() {
        return spawnTick;
    }

    public String encounterId() {
        return encounterId;
    }

    public Location location() {
        return entity.getLocation();
    }

    public boolean isValid() {
        return !removed && entity.isValid() && !entity.isDead();
    }

    public double healthPercent() {
        if (maxHealth <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (entity.getHealth() / maxHealth) * 100.0));
    }

    public int phaseIndex() {
        return phaseIndex;
    }

    public void setPhaseIndex(int phaseIndex) {
        this.phaseIndex = phaseIndex;
    }

    public LivingEntity target() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public boolean engaged() {
        return engaged;
    }

    public void setEngaged(boolean engaged) {
        this.engaged = engaged;
    }

    public long scaleLockTick() {
        return scaleLockTick;
    }

    public void setScaleLockTick(long tick) {
        this.scaleLockTick = tick;
    }

    public int scaledPlayers() {
        return scaledPlayers;
    }

    public void setScaledPlayers(int players) {
        this.scaledPlayers = players;
    }

    /** True if any player is close enough to see the boss bar (i.e. worth running abilities for). */
    public boolean hasNearbyPlayers() {
        return !viewers.isEmpty();
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    /** Keep the boss' spawn chunk loaded so a far-away spawn doesn't unload before players arrive. */
    public void setChunkTicket(World world, int cx, int cz) {
        this.ticketWorld = world;
        this.ticketCx = cx;
        this.ticketCz = cz;
        this.hasTicket = true;
    }

    public void releaseChunkTicket(org.bukkit.plugin.Plugin plugin) {
        if (hasTicket && ticketWorld != null) {
            ticketWorld.removePluginChunkTicket(ticketCx, ticketCz, plugin);
            hasTicket = false;
        }
    }

    public long fleeAtTick() {
        return fleeAtTick;
    }

    public void setFleeAtTick(long fleeAtTick) {
        this.fleeAtTick = fleeAtTick;
    }

    public Set<UUID> healers() {
        return healers;
    }

    public void addHealer(UUID uuid) {
        healers.add(uuid);
    }

    public double healerHealPerTick() {
        return healerHealPerTick;
    }

    public void setHealerHealPerTick(double healerHealPerTick) {
        this.healerHealPerTick = healerHealPerTick;
    }

    public double addMultiplier() {
        return addMultiplier;
    }

    public void setAddMultiplier(double addMultiplier) {
        this.addMultiplier = addMultiplier;
    }

    public long lastEnrageTick() {
        return lastEnrageTick;
    }

    public void setLastEnrageTick(long lastEnrageTick) {
        this.lastEnrageTick = lastEnrageTick;
    }

    /** True the first time only - guards death handling (and loot) from ever running twice. */
    public boolean beginDeath() {
        if (deathHandled) {
            return false;
        }
        deathHandled = true;
        return true;
    }

    /** The next enderman teleport is one WE scripted (via the teleport mechanic), so allow it. */
    public void markScriptedTeleport() {
        scriptedTeleport = true;
    }

    /** Consume the scripted-teleport flag: true = allow this teleport, false = it was involuntary. */
    public boolean consumeScriptedTeleport() {
        boolean b = scriptedTeleport;
        scriptedTeleport = false;
        return b;
    }

    /** Record damage a player dealt to this boss (for participation loot). */
    public void recordDamage(UUID player, double amount) {
        damageByPlayer.merge(player, amount, Double::sum);
    }

    public Map<UUID, Double> damageByPlayer() {
        return damageByPlayer;
    }

    // ---- skill timers ---------------------------------------------------------------------

    public long nextTick(int skillIndex) {
        return skillNextTick.getOrDefault(skillIndex, 0L);
    }

    public void setNextTick(int skillIndex, long tick) {
        skillNextTick.put(skillIndex, tick);
    }

    // ---- boss bar -------------------------------------------------------------------------

    /** Recompute the bar progress/title and show/hide it based on nearby players. */
    public void updateBossBar() {
        bossBar.progress((float) Math.max(0, Math.min(1, entity.getHealth() / maxHealth)));

        World world = entity.getWorld();
        Location loc = entity.getLocation();
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;

        Set<UUID> near = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                near.add(p.getUniqueId());
                if (viewers.add(p.getUniqueId())) {
                    p.showBossBar(bossBar);
                }
            }
        }
        Iterator<UUID> it = viewers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!near.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bossBar);
                }
                it.remove();
            }
        }
    }

    private void hideFromAll() {
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bossBar);
            }
        }
        viewers.clear();
    }

    /** Remove the boss bar. Optionally removes the entity too. */
    public void cleanup(boolean removeEntity) {
        if (removed) {
            return;
        }
        removed = true;
        hideFromAll();
        if (removeEntity && entity.isValid()) {
            entity.remove();
        }
    }

    /** The display name used on the bar and nametag (difficulty is intentionally not shown here). */
    public Component displayName() {
        return Text.mm(def.name());
    }
}
