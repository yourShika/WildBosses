# BetterModel integration

WildBosses can render bosses with custom [BetterModel](https://modrinth.com/plugin/bettermodel)
models and animations. Integration is **fully optional and automatic**:

- If the **BetterModel** plugin is installed and enabled, and a boss declares a `model:`, WildBosses
  attaches that model to the boss entity and drives its animations.
- If BetterModel is **not** installed (or the named model isn't loaded), the boss uses its vanilla
  `base-entity` appearance instead — nothing breaks, no configuration needed.

The integration lives in an optional module that is bundled into the single plugin jar but only
touched at runtime when BetterModel is present (it is loaded reflectively), so WildBosses never
hard-depends on BetterModel.

## Wiring a model to a boss

1. Install BetterModel (Paper 26.1-compatible build).
2. Put your BlockBench `.bbmodel` into BetterModel's models folder and note its **model name**
   (BetterModel loads and names it — see BetterModel's own docs).
3. In the boss file, set:

   ```yaml
   model: "warthoglin"        # the BetterModel model name
   animations:
     idle: idle               # state -> animation name in the model
     walk: walk
     attack: attack
     phase2: rage
   ```

4. `/wb reload`, then `/wb spawn warthoglin`.

WildBosses plays the `idle` animation on spawn and the animation named in a phase's `animation:`
field on phase change. Because the model covers the entity, the boss' name tag is hidden when a model
is active (the boss bar still shows the name and difficulty).

## Testing the fallback

Remove or disable BetterModel and run `/wb spawn warthoglin` again — the same boss now appears as a
vanilla Piglin Brute with all its abilities intact. This is the intended fallback behaviour.

## Version

Built against `io.github.toxicity188:bettermodel-api` / `bettermodel-bukkit-api` (BetterModel 3.x,
package `kr.toxicity.model.api`). If BetterModel makes a breaking API change, the adapter self-disables
and WildBosses falls back to vanilla appearances rather than failing.
