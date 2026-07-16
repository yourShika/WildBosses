# Configuration (`config.yml`)

All message strings are [MiniMessage](https://docs.advntr.dev/minimessage/) and support gradients.

```yaml
settings:
  random-spawns: true             # master switch for automatic spawning
  spawn-interval-seconds: 600     # how often a spawn cycle runs
  spawn-attempts-per-cycle: 1     # attempts each cycle (each still checks a boss' rules)
  max-active-bosses: 5            # global cap on active bosses (armies count as one)
  min-distance-between-bosses: 200
  boss-lifetime:                  # undefeated bosses/armies eventually flee (with a broadcast)
    enabled: true
    min-minutes: 30
    max-minutes: 60
  scaling:                        # scale a boss up when more players are nearby
    enabled: true
    radius: 48
    health-per-player: 0.25       # +25% max health per extra player (also scales summoned adds)
    max-multiplier: 4.0

rewards:
  participation-loot: true        # every player who damaged the boss gets their own loot roll

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

difficulties:                     # per-tier label, gradient and boss-bar colour
  EASY:       { label: "Easy",       from: "#7CFC00", to: "#2E8B57", bar: GREEN }
  MEDIUM:     { label: "Medium",     from: "#FFD700", to: "#FFB300", bar: YELLOW }
  HARD:       { label: "Hard",       from: "#FF8C00", to: "#FF2D00", bar: RED }
  ULTRA_HARD: { label: "Ultra Hard", from: "#FF1A4B", to: "#7A0000", bar: RED }
  MAGICAL:    { label: "Magical",    from: "#C86BFF", to: "#FF6BD6", bar: PURPLE }
```

Most of these are also editable in-game via `/wb gui → Settings`. Boss stats/difficulty/weight and
the terrain toggle are editable via `/wb gui → Bosses → shift-click a boss`.

Reload after manual edits with `/wb reload`.
