# Boss fights

A design overview of every boss currently in WildBosses — phases, abilities, AoEs and drops. This
matches the `bosses/*.yml` files; tune any number there and `/wb reload`.

## Systems that apply to every boss

- **Difficulty tiers** (Easy → Magical) — shown only in the spawn broadcast.
- **Enrage timer** (ultra bosses) — if not killed in time, damage/speed ramp every ~45 s.
- **Immunities** — some bosses ignore certain damage (projectile/fire/explosion/wither/...).
- **Player scaling** — more players nearby → more boss health and more summoned adds.
- **Participation loot** — everyone who dealt damage gets their own loot roll.
- **Flee timer** — an undefeated boss/army flees after 30–60 min (with a broadcast).
- **Dialogue** — bosses taunt with random lines on spawn, phase change and during the fight.
- **Telegraphed AoEs** — ground attacks draw a **filled particle disc** (with a sound) a moment before
  they strike, so you can step out. `aoe_damage`/`shockwave` also outline their blast radius.

Difficulty legend: 🟢 Easy · 🟡 Medium · 🟠 Hard · 🔴 Ultra Hard · 🟣 Magical

---

## 🟡 Mortis, the Zombie King — `zombie_king` · Overworld
Giant zombie (~460 HP) that spawns with **random enchanted gear**. Sculk-soul aura.

- **Phase 1 (100%)** — rises; summons 3 zombies every 6 s; poisons players within 4 blocks.
- **Phase 2 (50%, enrage)** — "calls the horde": a **slam AoE** (6-block, knockback) + summons 4 husks.
- **Phase 3 (25%, enrage)** — pounds the ground: a **ground-slam shockwave** (8-block, launches players up).
- **Drops** — diamonds, emeralds, *Crown Apple of Mortis* (enchanted golden apple), netherite scrap · 500 XP.

## 🟠 Ossaria, the Bonelord — `skeleton_king` · Overworld
Skeleton archer (~540 HP), **random enchanted gear**, soul aura.

- **Phase 1 (100%)** — arrow volleys at the nearest player; **arrow rain** falls on players (12-block area); summons strays.
- **Phase 2 (60%)** — raises a **bone shield** (absorption + resistance).
- **Phase 3 (30%, enrage)** — a massive **flaming arrow storm from the sky** (16-block, 40 arrows).
- **Drops** — diamonds, *Bonelord's Longbow* (Power V / Flame / Infinity), netherite ingot · 700 XP.

## 🟠 Kaboomicus, the Sundering King — `creeper_king` · Overworld
Big creeper (~480 HP), **immune to explosions**. **Never self-detonates on contact** — he fights.

- **Phase 1 (100%)** — flame aura; a controlled **block-safe explosion** when a player gets within 5 blocks (on cooldown); summons charged-looking creepers; knocks players back.
- **Phase 2 (50%, enrage)** — a larger block-safe **explosion**.
- **Phase 3 (15%)** — "If I fall, you fall with me!": a huge **final detonation** that kills him (block-safe).
- **Drops** — diamonds, TNT, *Crown of Kaboomicus* (creeper head) · 650 XP.

## 🔴 Nyxara, Queen of the Void — `enderman_queen` · The End
Enderman queen (~640 HP), portal aura, enrage timer.

- **Phase 1 (100%)** — blinks to the nearest player; **teleports random players** away; **pulls** everyone within 10 blocks toward her; summons endermites.
- **Phase 2 (60%)** — "flickers between spaces" (faster teleporting).
- **Phase 3 (30%, enrage)** — a **dragon-breath AoE** (8-block).
- **Drops** — ender pearls, diamonds, *Voidwing of Nyxara* (elytra), nether star · 1000 XP.

## 🟣 Aurelith, the Prism Mare — `magical_unicorn` · Overworld
Flying unicorn (~560 HP), rainbow aura, real damage.

- **Phase 1 (100%)** — a **prism beam** at the nearest player; **charms** players (nausea + slow); summons wisps (vexes); leaps.
- **Phase 2 (66%)** — heals itself (~90) and blazes with light.
- **Phase 3 (33%, enrage)** — **takes flight** (hovers/drifts), a **totem-light AoE** (7-block), and a speed buff.
- **Drops** — *Heart of Aurelith* (nether star), golden apples, diamonds · 900 XP.

## 🔴 Warthoglin, the Goldtusk King — `warthoglin` · Nether
Piglin Brute king (~720 HP), **immune to fire & knockback**, **never zombifies from lightning**, enrage timer.

- **Phase 1 (100%)** — flame aura; **leaps** at players; summons piglin brutes.
- **Signature: Axe Storm** — plants his feet and hurls **axes in every direction** (radial), and rains **meteors** (block-safe) on players.
- **Phase 2 (60%)** — roars, calls more brutes, and unleashes a **ground-slam shockwave** (8-block).
- **Phase 3 (30%, enrage)** — golden fury: a **flame AoE** (6-block, heavy knockback).
- **Drops** — gold blocks, *Goldtusk Ingot*, *Warthoglin's Cleaver* (Sharpness VI / Fire Aspect II) · 1200 XP.

## 🟢 The Goblin Warband → Grizznak the Plunderer — `goblin_army` · Overworld
An **army** encounter: **5 escalating waves** (slay 8 → 12 → 16 → 20 → 24). After the last wave, the
**Goblin King Grizznak** (~300 HP) emerges.
- **Waves** — goblin raiders (vindicators), grunts (zombies), slingers (pillagers).
- **Grizznak** — leaps, summons vindicators, knockback; enrages at 40%.
- **Drops** — emeralds, iron, *Plunderer's Prize* (golden apple) · 450 XP.

## 🟡 The Rotting Legion → Pestis, the Plaguebearer — `infected_army` · Overworld
An **army** of undead that **poison**, are **faster**, and **don't burn in daylight**. Spawns only on
frontier (ungenerated) chunks and **permanently infects the ground** (mycelium/podzol/soul sand) — the
corruption stays after the fight.
- **Waves** — infected, plagued husks, rotting zealots (all poison-on-hit). Slay 10 → 16 → 22 → 28.
- **Pestis** (~360 HP) — throws **lingering poison potions**, exhales **poison clouds**, poisons nearby players, summons poisonous husks. Enrages at 50%.
- **Drops** — rotten flesh, emeralds, diamonds, *Plaguebearer's Boon* · 550 XP.

## 🔴 Oberhexe Walak — `walak` · Overworld
Dark sorceress (evoker, ~600 HP), **immune to magic**, witch aura, enrage timer.

- **Phase 1 (100%)** — summons vexes; throws **harming potions**; blinks to players; rains **meteors**.
- **Phase 2 (60%)** — calls down **lightning** on players.
- **Phase 3 (30%, enrage)** — summons **healer attendants** (heal her until killed), a **soul-fire AoE** (8-block), and a resistance buff.
- **Drops** — *Walak's Phylactery* (totem), diamonds, enchanted book · 1000 XP.

## 🟠 Fenrar, the Bloodmoon (Werewolf) — `werewolf` · Overworld · **night only**
Fast beast (~500 HP), only hunts at night, doesn't burn in the sun.

- **Phase 1 (100%)** — **leaps** at players; **lifesteals** (heals off nearby players); summons a wolf pack; inflicts poison (bleed).
- **Phase 2 (50%, enrage)** — "Blood Moon!": big speed buff, relentless pursuit.
- **Drops** — leather, diamonds, *Bloodmoon Heart* (golden apples) · 700 XP.

## 🟠 Vespula, the Hive Queen (Queen Bee) — `queen_bee` · Overworld
Giant flying bee (~460 HP).

- **Phase 1 (100%)** — summons bees; **poison sting** (4-block); fires stinger projectiles; takes flight.
- **Phase 2 (50%, enrage)** — summons a **big swarm** (8 bees) and a heart-particle AoE.
- **Drops** — honeycombs, honey bottles, *Royal Jelly* (golden apple), diamonds · 650 XP.

## 🔴 Abyssos, the Leviathan — `leviathan` · Overworld (near water)
Deep-sea terror (elder guardian, ~780 HP), **immune to drowning & fire**, only spawns near water, enrage timer.

- **Phase 1 (100%)** — **pulls** players from 14 blocks; a **guardian beam** (14 dmg); summons guardians; inflicts mining fatigue (8-block).
- **Phase 2 (60%)** — drags the deep up with it.
- **Phase 3 (30%, enrage)** — a **maelstrom AoE** (10-block).
- **Drops** — prismarine crystals/shards, *Leviathan's Core* (heart of the sea), diamonds · 1100 XP.

## 🔴 Medusa of Bikini Bottom — `medusa` · Overworld
Gorgon witch (~580 HP), enrage timer.

- **Phase 1 (100%)** — **petrifying gaze** (heavy slow + blind, 10-block); throws poison potions; summons serpents (silverfish); a **telegraphed danger zone**.
- **Phase 2 (50%, enrage)** — a **wide petrify** (14-block) and a sneeze AoE.
- **Drops** — *Medusa's Gaze* (ender eyes), emeralds, diamonds · 950 XP.

## 🔴 Mortarion, the Harvester — `harvester` · Overworld / Nether
Reaper (wither skeleton with a scythe, ~700 HP), **immune to wither & fall**, enrage timer.

- **Phase 1 (100%)** — **lifesteal** (heals 75% of damage dealt, 4-block); a **scythe sweep** (radial); throws **wither potions**; summons wither skeletons; **telegraphed danger zones**.
- **Phase 2 (60%)** — summons **soul attendants** (heal him until killed).
- **Phase 3 (30%, enrage)** — a full **soul AoE** (8-block) and a huge **lifesteal** that fully heals him.
- **Drops** — wither skeleton skulls, netherite scrap, diamonds · 1100 XP.

---

*Test any fight with `/wb spawn <id>` (armies: `/wb army <id>`). All numbers live in
`plugins/WildBosses/bosses/<id>.yml`.*
