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
| `onTimer` | `interval` (ticks) | every interval |
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

## Drops

```yaml
drops:
  items:
    - { item: DIAMOND, amount: "3-6", chance: 1.0 }
    - { item: NETHERITE_SWORD, amount: 1, chance: 0.2, glow: true,
        name: "<gold>Legendary Blade", lore: ["<gray>Dropped by a king."],
        enchants: ["SHARPNESS:6", "FIRE_ASPECT:2"], custom-model-data: 1001 }
  xp: 500
  commands: ["eco give %player% 1000"]
```

## Army

```yaml
army:
  kill-threshold: 24        # kills to resolve
  wave-size: 6
  max-alive: 18
  reinforce-interval-ticks: 100
  radius: 12
  outcome: SPAWN_BOSS       # SPAWN_BOSS | FLEE | CLEARED
  end-boss: my_boss         # boss id spawned on SPAWN_BOSS (often this same file)
  timeout-seconds: 0        # 0 = no timeout
  minions:
    - { type: VINDICATOR, weight: 3, health: 24, name: "<green>Raider", effects: ["POISON:100:0"] }
```

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
