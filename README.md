# WildBosses

**Random custom world bosses for Paper/Spigot — Minecraft 26.1.2.**

WildBosses spawns unique, configurable bosses at random across the Overworld, Nether and End,
announces where they appear, and fights the players with phases, boss bars, custom abilities, AoEs,
custom drops, and army encounters — a MythicMobs-style engine where every boss is defined in YAML.
> Everything (config keys, in-game messages, docs) is English-first.

---

## Features

- **Random spawning** across Overworld / Nether / End, with a server broadcast of *which* boss and
  *where* (colourful MiniMessage gradients).
- **Difficulty tiers** — `Easy`, `Medium`, `Hard`, `Ultra Hard`, `Magical` — each with its own colour
  gradient, shown in the broadcast, boss bar and name tag.
- **YAML boss engine** — phases, boss bars, ~24 built-in ability mechanics, triggers, targeters and
  conditions. Add or retune bosses without touching code.
- **Custom stats & drops** — health, armor, damage, speed, scale, equipment; rolled item drops with
  MiniMessage names/lore/enchants, XP and reward commands.
- **Army encounters** — waves of minions spawn and reinforce until a kill threshold is reached, then
  an outcome resolves: an end-boss emerges, the survivors flee, or the horde is cleared. Its own
  progress boss bar shows kills.
- **Terrain theming ("world corruption")** — bosses can transform the ground around them (e.g. the
  Infected Army rots the earth). **Player builds are never destroyed** — see [Terrain safety](#terrain-safety).
- **In-game admin GUI** (`/wb gui`) — browse and spawn bosses, quick-edit common stats, manage active
  encounters, and toggle settings, all persisted back to the YAML/config.
- **Fight design** — telegraphed danger zones, ground-slam shockwaves, meteor rain, enrage timers,
  healer adds, per-boss damage immunities, and nearby-player scaling (health + adds).
- **Rewards** — participation loot (everyone who helped gets a roll), plus per-boss drops/XP/commands.
- **Integrations** — optional PlaceholderAPI (`%wildbosses_active%`, `%wildbosses_nearest%`, …) and a
  Discord webhook on spawn/kill.

---

## Requirements

| | |
|---|---|
| Server | Paper (or a Paper fork: Purpur, Folia, etc.) for **Minecraft 26.1.2** |
| Java | **25+** (required by MC 26.1) |
| Optional | CoreProtect / WorldGuard / GriefPrevention (extra terrain protection); PlaceholderAPI (placeholders) |

## Installation

1. Download `WildBosses-x.y.z.jar` (from the [Actions](../../actions) build artifacts or a release).
2. Drop it into your server's `plugins/` folder.
3. Start the server. Default config, language file and the 8 example bosses are written to
   `plugins/WildBosses/`.

## Commands & permissions

Base command: `/wildbosses` (alias `/wb`).

| Command | Description | Permission |
|---|---|---|
| `/wb spawn <id> [here\|player]` | Spawn a boss | `wildbosses.spawn` |
| `/wb army <id> [here\|player]` | Start an army encounter | `wildbosses.spawn` |
| `/wb list` | List registered bosses | `wildbosses.command` |
| `/wb active` | List active bosses/armies | `wildbosses.command` |
| `/wb info <id>` | Show a boss' details | `wildbosses.command` |
| `/wb gui` | Open the admin GUI | `wildbosses.gui` |
| `/wb killall` | Remove all active encounters | `wildbosses.admin` |
| `/wb reload` | Reload config and bosses | `wildbosses.admin` |
| `/wb update` | Download the latest release from GitHub (applied on restart) | `wildbosses.admin` |

`wildbosses.admin` (default: op) grants everything.

---

## The bosses

| Id | Name | Difficulty | Where | Notes |
|---|---|---|---|---|
| `goblin_army` | Grizznak the Plunderer | Easy | Overworld | 5 escalating waves → Goblin King |
| `infected_army` | Pestis, the Plaguebearer | Medium | Overworld | Poison army, permanent infection |
| `zombie_king` | Mortis, the Zombie King | Medium | Overworld | Random gear, summons, AoE |
| `skeleton_king` | Ossaria, the Bonelord | Hard | Overworld | Arrow rain, random gear |
| `creeper_king` | Kaboomicus, the Sundering King | Hard | Overworld | Detonates & dies (block-safe) |
| `enderman_queen` | Nyxara, Queen of the Void | Ultra Hard | End | Teleports, pulls, endermites |
| `magical_unicorn` | Aurelith, the Prism Mare | Magical | Overworld | Flies, prism beam, summons |
| `warthoglin` | Warthoglin, the Goldtusk King | Ultra Hard | Nether | Axe storm, leaps, summons, meteors |
| `walak` | Oberhexe Walak | Ultra Hard | Overworld | Dark magic, vexes, lightning |
| `werewolf` | Fenrar, the Bloodmoon | Hard | Overworld | Fast, leaps, lifesteal, wolves |
| `queen_bee` | Vespula, the Hive Queen | Hard | Overworld | Flies, swarms, poison sting |
| `leviathan` | Abyssos, the Leviathan | Ultra Hard | Overworld (water) | Pull, beam, guardians |
| `medusa` | Medusa of Bikini Bottom | Ultra Hard | Overworld | Petrifying gaze, serpents |
| `harvester` | Mortarion, the Harvester | Ultra Hard | Overworld/Nether | Lifesteal, scythe, wither |

Each boss taunts players with random dialogue, has ambient particles/sounds, telegraphed AoEs, and
(if undefeated) eventually flees. Difficulty is shown only in the spawn broadcast — not on the boss
bar or name tag. **Fight breakdown for every boss: [docs/boss-fights.md](docs/boss-fights.md).**
Authoring reference: [docs/boss-authoring.md](docs/boss-authoring.md).

---

## Terrain safety

Terrain-changing bosses never destroy player builds. Protection is layered:

1. **Frontier spawning (primary).** Terrain bosses only spawn where their whole footprint is on
   **never-generated chunks** — pristine land that has never been visited, so it cannot contain
   builds. If no such spot is found, the spawn is skipped (it never falls back to explored land).
2. **Allowlist only.** Only an explicit set of natural blocks (grass, dirt, sand, stone, …) is ever
   transformed. Building materials are never candidates.
3. **Block-entities & regions skipped.** Chests/signs/etc. are skipped; if WorldGuard or
   GriefPrevention is installed, protected/claimed areas are skipped.
4. **CoreProtect verification.** If CoreProtect is installed, any block with logged history is treated
   as player-placed and skipped.
5. **Snapshot + guaranteed restore.** Every changed block is recorded (with its original data) and
   persisted to disk, so terrain reverts when the encounter ends — and even a mid-encounter server
   restart restores it on next start.

Configure per boss under `terrain:` (see [docs/configuration.md](docs/configuration.md)).

---

## Configuration

- `config.yml` — global settings (spawn interval, world toggles, limits, broadcast formats,
  difficulty colours). Reference: [docs/configuration.md](docs/configuration.md).
- `bosses/*.yml` — one file per boss. Reference: [docs/boss-authoring.md](docs/boss-authoring.md).
- `lang/en.yml` — all player-facing messages (MiniMessage).

Reload after edits with `/wb reload`.

---

## Building from source

```bash
./gradlew build
```

Requires JDK 25. The plugin jar is produced at `core/build/libs/WildBosses-<version>.jar`. CI builds
every push (see `.github/workflows/build.yml`).

## License

[MIT](LICENSE).
