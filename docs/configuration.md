# Configuration (`config.yml`)

All message strings are [MiniMessage](https://docs.advntr.dev/minimessage/) and support gradients.

```yaml
settings:
  random-spawns: true             # master switch for automatic spawning
  spawn-interval:                 # each cycle waits a RANDOM time in [min, max] minutes (1 to 60+)
    min-minutes: 10
    max-minutes: 30
  spawn-interval-seconds: 600     # legacy fixed interval, used only if spawn-interval is removed
  spawn-attempts-per-cycle: 1     # attempts each cycle (each still checks a boss' rules)
  max-active-bosses: 5            # global cap on active bosses (armies count as one)
  min-distance-between-bosses: 200
  min-player-distance: 500        # normal bosses spawn >= this from EVERY player (only in already
                                  # generated/explored chunks); the spawn chunk is force-loaded until
                                  # a player arrives
  unclaimed-despawn-minutes: 8    # an un-fought boss despawns after this (frees its chunk); the full
                                  # flee lifetime only starts on first player contact
  disabled-bosses: []             # ids to switch OFF entirely (survives reload & restore), e.g. [medusa]
  boss-lifetime:                  # undefeated bosses/armies eventually flee (with a broadcast)
    enabled: true
    min-minutes: 30
    max-minutes: 60
  scaling:                        # scale a boss up to how many players ENGAGE it (first contact)
    enabled: true
    radius: 48
    per-player-multiplier: 1.5    # each attacker beyond the first multiplies health/damage
    max-multiplier: 5.0           # kept for the whole fight even if players die/flee
    engage-window-seconds: 10     # after first contact, re-match the growing crowd for this long (take max)

rewards:
  participation-loot: true        # every player who damaged the boss gets their own loot roll
  drop-count: { min: 1, max: 3 }  # cap items dropped per kill to a random 1-3 (rarest kept); all announced

integrations:
  discord-webhook: ""             # optional: posts to Discord on boss spawn/kill (leave blank to disable)
  frontier-search:                # placement search for terrain (frontier-only) bosses
    min-distance: 200
    max-distance: 3000
    attempts: 24

worlds:                           # enable/disable spawning per dimension
  OVERWORLD: true
  NETHER: true
  THE_END: true

broadcast:
  enabled: true
  # Placeholders: <boss> <difficulty> <world> <x> <y> <z>
  boss-spawn: "... <boss> <difficulty> ... <world> ... <x>, <z> ..."
  army-spawn: "..."
  boss-death: "..."
  boss-fled: "..."
  # Announce notable item drops. Placeholders: <player> <item> <amount> <boss> <difficulty>
  boss-drop: "... <player> looted <item> from the <boss> ..."
  drops:
    enabled: true                 # master toggle for drop announcements
    announce-threshold: 0.5       # auto-announce drops with chance <= this (0..1); rarer = louder
  death-sound: "ui.toast.challenge_complete"  # played to every online player on a boss kill (blank = off)

difficulties:                     # per-tier label, gradient and boss-bar colour
  EASY:       { label: "Easy",       from: "#7CFC00", to: "#2E8B57", bar: GREEN }
  MEDIUM:     { label: "Medium",     from: "#FFD700", to: "#FFB300", bar: YELLOW }
  HARD:       { label: "Hard",       from: "#FF8C00", to: "#FF2D00", bar: RED }
  ULTRA_HARD: { label: "Ultra Hard", from: "#FF1A4B", to: "#7A0000", bar: RED }
  MAGICAL:    { label: "Magical",    from: "#C86BFF", to: "#FF6BD6", bar: PURPLE }
```

Most of these are also editable in-game via `/wb gui → Settings`. Boss stats/difficulty/weight and
the terrain toggle are editable via `/wb gui → Bosses → shift-click a boss`.

`/wb reload` reloads config + bosses and **re-creates any missing default boss file**.
`/wb restore default` rewrites every bundled boss file **and** `config.yml` back to defaults.

## Commands

| Command | Who | What |
|---|---|---|
| `/wb list` | everyone (`wildbosses.list`) | opens the read-only **bestiary** (all bosses, drops + chances) |
| `/wb active` | op | lists live bosses with coordinates and the **flee timer** |
| `/wb spawn <id\|random> [here\|player]` | `wildbosses.spawn` | spawn a boss; `random` picks one |
| `/wb army <id\|random> [here\|player]` | `wildbosses.spawn` | start an army; `random` picks one |
| `/wb gui` | `wildbosses.gui` | admin GUI (bosses, drops editor, active, settings) |
| `/wb killall` · `/wb reload` · `/wb restore default` · `/wb update` | `wildbosses.admin` | admin actions |

## Lag-clearer / anti-mob-clear plugins

Every WildBosses entity (bosses, army minions, summoned adds) is given the scoreboard tag
**`wildbosses`** and a custom name. If a lag plugin (ClearLagg, LagAssist, Insights, …) is removing
them, add `wildbosses` to that plugin's mob **whitelist / safelist** (or tell it to skip named /
tagged mobs). Bosses also have `removeWhenFarAway = false` so they don't despawn on their own.

## Terrain replacement

A terrain-changing boss/army replaces natural ground using `terrain.mappings: { FROM: TO }` — you
choose exactly which block becomes which. Set `terrain.restore-on-end: false` to make the change
**permanent** (the blocks stay); `true` restores the original ground when the encounter ends. Player
builds are never touched, and terrain encounters only spawn on never-generated frontier chunks.
