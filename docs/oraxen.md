# Custom textures & Oraxen

WildBosses can give each boss a custom look in one of two ways:

1. **BetterModel** — a full BlockBench model + animations (see [bettermodel.md](bettermodel.md)). Best
   quality; use this if you have a model.
2. **Custom texture via Oraxen** — a single texture (or item model) shown on the mob via an
   `ItemDisplay`. No BetterModel needed. *(Experimental — test it on your server.)*

Rendering is tried in order: **BetterModel → Oraxen texture → vanilla appearance**. If none applies,
the boss just uses its `base-entity`.

## Folders (filename = boss id)

On first run WildBosses creates two folders in its data folder:

```
plugins/WildBosses/models/      <bossId>.bbmodel   e.g. warthoglin.bbmodel  (BlockBench model → BetterModel)
plugins/WildBosses/textures/    <bossId>.png       e.g. medusa.png          (flat texture, no BetterModel)
```

The file name must match the boss id (the `id:` in `bosses/<id>.yml`).

## The pipeline (`/wb assets redeploy`)

`/wb assets redeploy` (or automatically on startup) wires everything into **one** Oraxen pack:

1. **`models/<id>.bbmodel` → BetterModel.** Copied into `plugins/BetterModel/models/`. BetterModel
   loads the model (used by the boss whose id matches) and builds its pack
   `plugins/BetterModel/pack/BetterModel-Pack.zip`.
2. **BetterModel pack → Oraxen.** That zip is copied to `Oraxen/pack/uploads/bettermodel.zip`. Oraxen
   merges every zip in `pack/uploads/` into its generated pack, so BetterModel's models + textures
   ship inside the Oraxen texture pack.
3. **`textures/<id>.png` → Oraxen item.** For bosses without a BetterModel model, the flat texture is
   copied to `Oraxen/pack/textures/wildbosses/<id>.png` and registered as an Oraxen item
   `wildbosses_<id>` (`generate_model: true`). It is shown on an `ItemDisplay` on the boss.

Existing files are backed up to `plugins/WildBosses/AssetBackups/` before being overwritten.

### Order of commands

```
# after adding/changing .bbmodel or .png files:
/wb assets redeploy     # install .bbmodel into BetterModel, hand its pack to Oraxen, deploy textures
/bettermodel reload     # rebuild BetterModel-Pack.zip from the new models
/wb assets redeploy     # copy the freshly-built BetterModel pack into Oraxen uploads
/oraxen reload          # rebuild + re-serve the merged pack
```

`/wb assets status` shows whether BetterModel/Oraxen are present, how many `.bbmodel`/`.png` you have,
and whether BetterModel has built its pack yet. Auto-deploy on startup:
`integrations.oraxen.auto-deploy: true|false`.

## Important: flat textures can't wrap a mob

A raw `.png` has no UV mapping onto a 3D mob, so it **cannot** be "painted" onto the mob's body — that
is a Minecraft limitation. To put a texture *on the mob shape* you need a **BlockBench model**
(`.bbmodel` via BetterModel), which maps the texture to the model.

Because of this, the flat-texture path is **off by default**. If you enable
`integrations.oraxen.flat-texture-display: true`, a boss without a model is shown as a flat **2D
sprite** (a billboard that always faces the player) on an invisible mob — useful for simple
2D-sprite enemies, but it is not a 3D textured mob.

- **For a real textured mob → use a `.bbmodel` (BetterModel).** That is the recommended path.
- Rendering falls back automatically: **BetterModel → (optional) Oraxen flat sprite → vanilla**.
