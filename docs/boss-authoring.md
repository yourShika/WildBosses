# Boss authoring

Each boss is one file in `plugins/WildBosses/bosses/<id>.yml`. Reload with `/wb reload`.

## Top-level keys

```yaml
id: my_boss                 # optional; defaults to the file name
name: "<gradient:#f00:#900>My Boss</gradient>"   # MiniMessage (gradients supported)
title: "<gray>A Fearsome Foe"                    # optional subtitle
difficulty: HARD            # EASY | MEDIUM | HARD | ULTRA_HARD | MAGICAL
base-entity: ZOMBIE         # any living EntityType
model: ""                   # BetterModel model name; "" = vanilla appearance
animations: { idle: idle, attack: attack, phase2: rage }  # state -> animation name
stats:
  health: 400
  armor: 12
  armor-toughness: 4
  knockback-resistance: 0.6
  damage: 9
  speed: 0.26               # movement speed attribute
  follow-range: 40
  scale: 1.5                # entity scale (1.0 = normal)
equipment: { hand: IRON_SWORD, offhand: SHIELD, head: GOLDEN_HELMET, chest: , legs: , feet: }
bossbar: { color: GREEN, style: NOTCHED_10 }     # color optional (defaults to difficulty colour)
spawn:
  worlds: [OVERWORLD, NETHER, THE_END]
  weight: 20               # relative random-spawn weight
  min-players-distance: 40
  y: { min: 40, max: 110 }
  cooldown-seconds: 1800
  max-concurrent: 1
phases:
  - { at-health-percent: 100, message: "<green>It begins!" }
  - { at-health-percent: 50, enrage: true, animation: rage, message: "<red>Enraged!" }
skills: [ ... ]             # see below
drops: { items: [ ... ], xp: 500, commands: [] }
terrain: { ... }            # optional, see Terrain
army: { ... }               # optional, see Army
```

`bossbar.style`: `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`.
`bossbar.color`: `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`.

### Optional top-level blocks

```yaml
immunities: [PROJECTILE, KNOCKBACK, FIRE, FALL, DROWNING, EXPLOSION, WITHER, MAGIC, POISON]
enrage-timer: { enabled: true, after-seconds: 300, interval-seconds: 45, damage-mult: 1.12, speed-mult: 1.05 }
```

- **immunities** — damage the boss ignores. `KNOCKBACK` is applied as full knockback resistance.
- **enrage-timer** — after `after-seconds`, the boss' damage/speed ramp every `interval-seconds`.

Extra `spawn:` conditions (checked by the random scheduler):

```yaml
spawn:
  time: NIGHT            # ANY | DAY | NIGHT
  biomes: [OCEAN, RIVER] # substring match against the biome key (any match)
  near-water: true       # only where water is within ~6 blocks
```

## Skills

A skill is `trigger → mechanic @ targeter ?conditions`:

```yaml
skills:
  - trigger: onTimer         # when it fires
    interval: 100            # trigger param (ticks)
    mechanic: summon         # what it does
    targeter: self_location  # who/where
    cooldown: 0              # optional min ticks between activations
    conditions:              # optional guards (all must pass)
      - { type: health_below, value: 50 }
    params: { type: ZOMBIE, amount: 3, radius: 4 }
```

### Triggers

| Trigger | Params | Fires |
|---|---|---|
| `onSpawn` | | once, on spawn |
| `onTimer` | `interval` (ticks), optional `interval-max` | every interval; with `interval-max` it fires at a random cadence in `[interval, interval-max]` so summons/attacks aren't robotic |
| `onDamaged` | | when the boss takes damage |
| `onDealDamage` | | when the boss deals melee damage |
| `onPhaseChange` | `phase` (optional) | on phase change (matching `phase` index) |
| `onHealthBelow` | `value` (%) | once, when health first drops below `value` |
| `onTargetInRange` | `radius` | when a player is within `radius` (rate-limited) |
| `onDeath` | | on death |

### Targeters

`self`, `self_location`, `current_target`, `nearest_player` (`radius`),
`players_in_radius` / `all_players_in_radius` (`radius`), `random_nearby` (`radius`).

### Conditions

`health_below`/`health_above` (`value`), `phase_equals`/`phase_at_least` (`phase`),
`chance` (`value` 0-1), `world_is` (`value: [OVERWORLD, ...]`), `players_in_radius` (`radius`, `count`).

### Mechanics

| Mechanic | Key params |
|---|---|
| `message` | `message`, `broadcast` |
| `sound` | `sound` (e.g. `entity.wither.spawn`), `volume`, `pitch` |
| `particle` | `particle`, `count`, `spread`, `speed`, `offset-y` |
| `damage` | `amount` |
| `aoe_damage` | `radius`, `damage`, `knockback`, `particle` |
| `summon` | `type`, `amount`, `radius`, `health`, `baby`, `effects: ["POISON:200:0"]` |
| `potion` | `type`, `duration`, `amplifier` |
| `potion_cloud` | `type`, `duration`, `amplifier`, `radius`, `cloud-duration` |
| `poison` | `duration`, `amplifier` |
| `knockback` | `strength`, `vertical` |
| `pull` | `strength` |
| `leap` | `power`, `vertical` |
| `teleport` | `radius` (boss teleports to/near target) |
| `teleport_target` | `radius` (target teleported near boss) |
| `lightning` | `damage` (bool) |
| `explode` | `power`, `fire` — never breaks blocks |
| `arrow_volley` | `count`, `spread`, `velocity`, `fire` |
| `beam` | `damage`, `particle` |
| `charm` | `duration` |
| `buff` | `type`, `duration`, `amplifier` (on the boss) |
| `heal` | `amount` |
| `shield` | `duration`, `amplifier` (absorption + resistance) |
| `command` | `command` (`%boss%`, `%player%`) |
| `projectile` | `type` (`FIREBALL`/`SMALL_FIREBALL`/`WITHER_SKULL`/`SNOWBALL`/`ARROW`), `velocity`, `yield`, `fire` |
| `say` / `taunt` | `lines: [ ... ]` (a random line is spoken as `[BossName]: ...`), `radius`, `prefix` |
| `arrow_rain` | `count`, `spread`, `height`, `damage`, `fire` — arrows fall from the sky onto targets |
| `throw_potion` | `type`, `duration`, `amplifier`, `lingering` (bool), `velocity` — throws a splash/lingering potion |
| `petrify` | `duration` — heavy slowness + mining fatigue + blindness ("turn to stone") |
| `lifesteal` | `amount`, `heal-ratio` — damage targets and heal the boss |
| `fly` | `duration`, `lift` — the boss lifts off and hovers/drifts toward its target (plays the `fly` animation) |
| `radial` / `axe_throw` | `count`, `velocity`, `type`, `particle` — launches projectiles evenly in all directions |
| `danger_zone` | `radius`, `damage`, `delay`, `knockback`, `warn-particle`, `particle` — telegraphed AoE (warning ring, then it strikes) |
| `shockwave` | `radius`, `damage`, `knockup`, `particle` — ground-slam ring that damages and launches players up |
| `meteor` / `meteor_rain` | `count`, `radius`, `height`, `damage`, `delay`, `fire` — meteors fall on marked spots (block-safe) |
| `healer_adds` | `type`, `amount`, `radius`, `heal-per-second`, `name` — spawns adds that heal the boss until killed |

`explode` also accepts `kill-caster: true` — the boss dies from its own blast (used by the Creeper King's final detonation). Explosions never break blocks regardless.

### Dialogue

Bosses "speak" via the `say`/`taunt` mechanic on any trigger. Put multiple `lines:` and a random one
is chosen and broadcast to nearby players as `[<boss name>]: <line>`:

```yaml
- { trigger: onPhaseChange, phase: 1, mechanic: say, targeter: nearest_player,
    params: { radius: 40, lines: ["You've angered me now!", "Enough games!"] } }
```

### Random equipment

Instead of fixed `equipment:`, a boss can spawn with randomised, enchanted gear. Enchants are pulled
from the enchantment registry, so vanilla **and** datapack custom enchantments are used automatically:

```yaml
random-equipment:
  enabled: true
  armor-tiers: [IRON, GOLDEN, DIAMOND]        # armor built as <tier>_HELMET etc.
  weapons: [IRON_SWORD, DIAMOND_SWORD, IRON_AXE]
  enchant-count: 2                            # random enchants per item
  extra-levels: 1                             # levels above each enchant's max (over-enchant)
```

## Drops

```yaml
drops:
  items:
    - { item: DIAMOND, amount: "3-6", chance: 1.0 }
    - { item: NETHERITE_SWORD, amount: 1, chance: 0.2, glow: true, announce: true,
        name: "<gold>Legendary Blade", lore: ["<gray>Dropped by a king."],
        enchants: ["SHARPNESS:6", "FIRE_ASPECT:2"], custom-model-data: 1001 }
  xp: 500
  commands: ["eco give %player% 1000"]
```

Each item is rolled independently: `chance` is `0.0`–`1.0` (e.g. `0.2` = 20%). A successful roll
drops the item (at each contributor's feet when `participation-loot` is on).

**Rarity tiers.** Give a drop a `rarity:` — `COMMON`, `UNCOMMON`, `RARE`, `LEGENDARY`, or `MYTHICAL`.
It colours a line in the item's lore and in the drop broadcast, and sets sensible defaults:
`RARE`+ **glow** on the ground, `LEGENDARY`+ are **always announced**. Suggested chances per tier:
Common `1.0`, Uncommon `~0.6–0.85`, Rare `~0.3–0.5`, Legendary `~0.15–0.25`, Mythical `~0.03–0.06`.
You can still override glow/announce explicitly per drop. Rarity is also editable in-game
(`/wb gui` → Bosses → shift-click → Drops → **middle-click** cycles the tier).

**Drop announcements.** When a boss dies, notable drops are broadcast in chat with a hoverable item
name. A drop is announced when either:
- its `chance` is at or below `broadcast.drops.announce-threshold` in `config.yml` (default `0.5`), or
- the drop sets `announce: true` (always broadcast, whatever its chance).

Turn the whole feature off with `broadcast.drops.enabled: false`. Customise the message via
`broadcast.boss-drop` (placeholders `<player> <item> <amount> <boss> <difficulty>`).

**Chance-based command rewards (e.g. pets).** Alongside `items`, a `command-rewards` list runs
console commands as loot, each rolled independently. `%player%` is the receiver, `%boss%` the boss id.
An optional `announce` line is broadcast (with `<player>`) when it drops. Great for granting a pet
from another plugin such as [BetterPets](https://github.com/yourShika/betterpets-paper)
(`/pets give <pet> [level] [player]`):

```yaml
drops:
  command-rewards:
    - { command: "pets give phoenix 1 %player%", chance: 0.06,
        announce: "<gold>claimed a Phoenix pet from the boss!</gold>" }
```

Adjust the pet id (`phoenix`, `bee`, `pufferfish`, `axolotl`, …) to match your BetterPets setup.

**Aerial AoE.** `danger_zone` takes an optional `offset-y` that lifts the telegraph off the ground,
so the danger area forms in the air above the target instead of on the floor (used by the Harvester).

**Safe placement.** Summoned minions, `healer_adds`, and all teleports snap to a safe standing spot,
so nothing ever spawns or teleports inside a block. **Drop chances** are set per item with `chance`
(`0.0`–`1.0`); notable drops glow and are announced. Editing chances and announce flags is also
possible in-game via `/wb gui` → Bosses → (shift-click a boss) → **Drops**.

## Army

```yaml
army:
  stages: [8, 12, 16, 20, 24]   # kill target per wave (increasing). Omit for a single wave.
  kill-threshold: 24            # fallback target if 'stages' is omitted
  wave-size: 6
  max-alive: 18
  reinforce-interval-ticks: 100
  radius: 12
  outcome: SPAWN_BOSS           # SPAWN_BOSS | FLEE | CLEARED (after the final wave)
  end-boss: my_boss             # boss id spawned on SPAWN_BOSS (often this same file)
  timeout-seconds: 0            # 0 = use the global boss-lifetime; otherwise a fixed limit
  minions:
    - { type: VINDICATOR, weight: 3, health: 24, name: "<green>Raider", effects: ["POISON:100:0", "SPEED:99999:0"] }
```

With `stages`, players must slay more each wave; the army bar shows `Wave x/n`. Zombie-type minions
never burn in daylight. Give minions a permanent `SPEED` effect (large duration) to make them faster.

When `outcome: SPAWN_BOSS` points `end-boss` at the same file, the file's top-level statline is the
army's leader that emerges once the horde is beaten.

## Terrain

```yaml
terrain:
  enabled: true
  radius: 7
  max-blocks: 4000
  restore-on-end: true
  require-coreprotect: false     # if true, only corrupt when CoreProtect confirms a block is natural
  only-ungenerated-chunks: true  # spawn only on pristine frontier chunks (keep this on)
  mappings:                      # allowlist: only these blocks are ever changed
    GRASS_BLOCK: MYCELIUM
    DIRT: PODZOL
    SAND: SOUL_SAND
```

See the main README's [Terrain safety](../README.md#terrain-safety) for how builds are protected.
