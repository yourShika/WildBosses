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
plugins/WildBosses/textures/    <bossId>.png   e.g. warthoglin.png
plugins/WildBosses/models/      <bossId>.json  e.g. warthoglin.json (optional custom item model)
```

The file name must match the boss id (the `id:` in `bosses/<id>.yml`).

## Deploying into the Oraxen pack

`/wb assets redeploy` (or automatically on startup) copies your assets into Oraxen and generates the
item definitions, so a single Oraxen resource pack ships every boss:

- `textures/<id>.png` → `Oraxen/pack/textures/wildbosses/<id>.png` + an Oraxen item `wildbosses_<id>`
  (`generate_model: true`, `parent_model: item/generated`).
- `models/<id>.json` → `Oraxen/pack/models/wildbosses/<id>.json`, referenced by the item.
- Existing Oraxen pack files are backed up (to `plugins/WildBosses/AssetBackups/`) before being
  overwritten.

After deploying, **run `/oraxen reload`** so Oraxen rebuilds and re-serves the pack.

```
/wb assets status     # shows whether Oraxen is present and how many textures/models you have
/wb assets redeploy   # (re)deploys into Oraxen; then run /oraxen reload
```

Auto-deploy on startup can be turned off with `integrations.oraxen.auto-deploy: false`.

## How the texture reaches the mob

Once deployed and `/oraxen reload`-ed, WildBosses builds the Oraxen item `wildbosses_<id>` and shows
it on an `ItemDisplay` mounted on the (invisible) base mob. So the boss "wears" your texture as its
model. Because it is a flat/2D item model by default, a purpose-built BetterModel model still looks
best for complex creatures — but this path needs no extra plugin beyond Oraxen.

## BetterModel + Oraxen together

BetterModel has its own Oraxen integration to merge its generated pack into Oraxen — enable that in
BetterModel's config if you want a single pack. WildBosses' asset deploy handles only its own
`textures/`/`models/`.
