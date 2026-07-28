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

    // Live boss-bar title/flash state.
    private String barBaseName;                 // the translated boss name shown on the bar
    private final BossBar.Color baseBarColor;   // restored after an enrage flash
    private long enrageFlashUntil;

    public ActiveBoss(BossDefinition def, LivingEntity entity, BossBar bossBar,
                      double maxHealth, long spawnTick, String encounterId) {
        this.def = def;
        this.entity = entity;
        this.bossBar = bossBar;
        this.maxHealth = maxHealth;
        this.spawnTick = spawnTick;
        this.encounterId = encounterId;
        this.baseBarColor = bossBar.color();
    }

    /** The (translated) name the live bar title is built from. Set once, right after spawn. */
    public void setBarBaseName(String mini) {
        this.barBaseName = mini;
    }

    /** Flash the boss bar red until {@code untilTick} to telegraph an enrage. */
    public void flashEnrage(long untilTick) {
        this.enrageFlashUntil = untilTick;
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

    public void releaseChunkTicket(com.yourshika.wildbosses.WildBossesPlugin plugin) {
        if (hasTicket && ticketWorld != null) {
            plugin.chunkTickets().release(ticketWorld, ticketCx, ticketCz);
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

    private final Set<UUID> summons = new HashSet<>();

    public void addSummon(UUID uuid) {
        summons.add(uuid);
    }

    /** Count of still-alive summoned adds + healers (prunes dead/removed entries as it counts). */
    public int liveAddCount() {
        return pruneAlive(summons) + pruneAlive(healers);
    }

    private static int pruneAlive(Set<UUID> set) {
        int n = 0;
        Iterator<UUID> it = set.iterator();
        while (it.hasNext()) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(it.next());
            if (e == null || e.isDead()) {
                it.remove();
            } else {
                n++;
            }
        }
        return n;
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

    private final TickDamageCap tickDamageCap = new TickDamageCap();
    private final CastRateLimiter castLimiter = new CastRateLimiter();

    /**
     * Anti-spam gate: whether this boss may cast an ability on {@code tick} without exceeding
     * {@code maxPerSecond} casts in the current one-second window. Returns false to refuse the cast.
     */
    public boolean allowCast(long tick, int maxPerSecond) {
        return castLimiter.allow(tick, maxPerSecond);
    }

    /**
     * Cumulative one-shot cap: how much of {@code incomingFinal} may actually be applied on
     * {@code serverTick} so the TOTAL damage this boss takes in a single tick never exceeds
     * {@code capTotal}. Guards against a single swing whose several bonus-damage procs would each be
     * capped individually yet still sum past 100%. See {@link TickDamageCap}.
     */
    public double allowDamageThisTick(int serverTick, double capTotal, double incomingFinal) {
        return tickDamageCap.allow(serverTick, capTotal, incomingFinal);
    }

    public Map<UUID, Double> damageByPlayer() {
        return damageByPlayer;
    }

    // ---- interruptible cast / channel -----------------------------------------------------

    private boolean casting;
    private long castEndTick;
    private double castInterruptDamage;
    private double castDamageTaken;
    private Runnable castPayload;
    private String castParticle;

    public boolean isCasting() {
        return casting;
    }

    public long castEndTick() {
        return castEndTick;
    }

    public String castParticle() {
        return castParticle;
    }

    /** Begin a channel that runs {@code payload} at {@code endTick} unless interrupted first. */
    public void startCast(long endTick, double interruptDamage, Runnable payload, String particle) {
        this.casting = true;
        this.castEndTick = endTick;
        this.castInterruptDamage = interruptDamage;
        this.castDamageTaken = 0;
        this.castPayload = payload;
        this.castParticle = particle;
    }

    /** Feed damage taken during a channel; returns true if it now exceeds the interrupt threshold. */
    public boolean addCastDamage(double amount) {
        if (!casting) {
            return false;
        }
        castDamageTaken += amount;
        return castInterruptDamage > 0 && castDamageTaken >= castInterruptDamage;
    }

    /** Cancel the channel (interrupted) - the payload never runs. */
    public void interruptCast() {
        casting = false;
        castPayload = null;
    }

    /** Finish the channel and run its payload (if any). */
    public void completeCast() {
        Runnable payload = castPayload;
        casting = false;
        castPayload = null;
        if (payload != null) {
            payload.run();
        }
    }

    // ---- invulnerability / shield-break (FF-style setpieces) -------------------------------

    private boolean invulnerable;
    private long invulnUntilTick;                       // >0 = time-based expiry
    private final Set<UUID> shieldAnchors = new HashSet<>();
    private long shieldDoomTick;                        // >0 = doom timer active
    private Runnable shieldDoom;

    /** True while the boss takes no damage (except VOID/KILL) - see BossListener. */
    public boolean isInvulnerable() {
        return invulnerable;
    }

    /** Become invulnerable until {@code untilTick} (a timed "immune while I channel my ultimate"). */
    public void setInvulnerable(long untilTick) {
        this.invulnerable = true;
        this.invulnUntilTick = untilTick;
        this.shieldDoomTick = 0;
        this.shieldAnchors.clear();
        this.shieldDoom = null;
    }

    public long invulnUntilTick() {
        return invulnUntilTick;
    }

    /** End a timed invulnerability. */
    public void clearInvulnerable() {
        this.invulnerable = false;
        this.invulnUntilTick = 0;
    }

    /**
     * Become invulnerable and gate it on anchor entities: destroying them all drops the shield; if the
     * doom timer elapses first, the doom payload (ultimate) fires. See BossManager.processShield.
     */
    public void startShield(Set<UUID> anchors, long doomTick, Runnable doom) {
        this.invulnerable = true;
        this.invulnUntilTick = 0;
        this.shieldAnchors.clear();
        this.shieldAnchors.addAll(anchors);
        this.shieldDoomTick = doomTick;
        this.shieldDoom = doom;
    }

    public Set<UUID> shieldAnchors() {
        return shieldAnchors;
    }

    public long shieldDoomTick() {
        return shieldDoomTick;
    }

    /** Drop the anchor shield; returns the doom payload (only meaningful when timed out). */
    public Runnable clearShield() {
        Runnable d = shieldDoom;
        this.invulnerable = false;
        this.invulnUntilTick = 0;
        this.shieldAnchors.clear();
        this.shieldDoomTick = 0;
        this.shieldDoom = null;
        return d;
    }

    // ---- skill timers ---------------------------------------------------------------------

    public long nextTick(int skillIndex) {
        return skillNextTick.getOrDefault(skillIndex, 0L);
    }

    public void setNextTick(int skillIndex, long tick) {
        skillNextTick.put(skillIndex, tick);
    }

    // ---- boss bar -------------------------------------------------------------------------

    /**
     * Refresh the bar's progress and live title (name + HP% + phase + enrage flash). Cheap - no player
     * scan - so it can run a few times a second without the per-tick cost of {@link #refreshViewers()}.
     */
    public void refreshBar(long tick) {
        float progress = maxHealth <= 0 ? 0f
                : (float) Math.max(0, Math.min(1, entity.getHealth() / maxHealth));
        bossBar.progress(progress);
        boolean flashing = tick < enrageFlashUntil;
        double pct = healthPercent();
        String base = barBaseName != null ? barBaseName : def.name();
        int phases = def.phases().size();
        String phaseTag = phases > 1 && phaseIndex >= 0 ? " <dark_gray>P" + (phaseIndex + 1) : "";
        String title = base + " <dark_gray>[<" + hpColor(pct) + ">" + (int) Math.ceil(pct) + "%<dark_gray>]"
                + phaseTag + (flashing ? " <red><bold>⚡" : "");
        bossBar.name(Text.mm(title));
        bossBar.color(flashing ? BossBar.Color.RED
                : (baseBarColor != null ? baseBarColor : BossBar.Color.WHITE));
    }

    private static String hpColor(double pct) {
        if (pct > 60) {
            return "green";
        }
        return pct > 30 ? "yellow" : "red";
    }

    /** Show/hide the bar based on which players are in range. The per-tick-expensive part. */
    public void refreshViewers() {
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
