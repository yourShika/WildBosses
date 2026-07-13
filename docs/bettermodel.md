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

## Model-by-name convention

You do **not** have to set `model:` at all. If a BetterModel model is loaded whose name matches the
**boss id**, WildBosses uses it automatically. So a model named `warthoglin` is used by the
`warthoglin` boss, `medusa` by the `medusa` boss, and so on. Set `model:` only to point a boss at a
differently-named model.

Put your models in BetterModel's models folder (they can be shipped in an **Oraxen** resource pack —
BetterModel integrates with Oraxen; see BetterModel's docs). The model name just has to match the
boss id.

## Automatic animation states

WildBosses drives model animations by the boss' state each tick. If your model contains an animation
with one of these names, it plays automatically — otherwise it's simply skipped:

`idle`, `walk`, `sprint`, `attack`, `target`, `death`, `fly`, `swim`

You don't need to configure anything for these; just name your BlockBench animations accordingly.
Phase animations are separate: a phase's `animation:` field plays that named animation on phase
change (e.g. a `rage` animation). If your model's animation names differ from the state names, map
them with an `animations:` block:

```yaml
animations: { idle: idle_loop, walk: walk_cycle, attack: swing, death: die }
```

## Wiring a model to a boss (explicit)

1. Install BetterModel (Paper 26.1-compatible build).
2. Load your BlockBench `.bbmodel` in BetterModel and name it after the boss id (e.g. `warthoglin`).
3. `/wb reload`, then `/wb spawn warthoglin`.

Because the model covers the entity, the boss' name tag is hidden while a model is active (the boss
bar still shows the name — difficulty is only shown in the spawn broadcast).

## Testing the fallback

Remove or disable BetterModel and run `/wb spawn warthoglin` again — the same boss now appears as a
vanilla Piglin Brute with all its abilities intact. This is the intended fallback behaviour.

## Version

Built against `io.github.toxicity188:bettermodel-api` / `bettermodel-bukkit-api` (BetterModel 3.x,
package `kr.toxicity.model.api`). If BetterModel makes a breaking API change, the adapter self-disables
and WildBosses falls back to vanilla appearances rather than failing.
