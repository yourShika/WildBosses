# Custom models (BetterModel) + Oraxen pack merge

Bosses get their custom look from **BetterModel** (BlockBench models + animations). If you also run
**Oraxen** and serve a single Oraxen resource pack, WildBosses merges BetterModel's generated pack
into Oraxen so everything ships in one pack.

> There is no "flat texture" path — a raw PNG can't be wrapped onto a 3D mob. Use a `.bbmodel`.

## Folder (filename = boss id)

```
plugins/WildBosses/models/<bossId>.bbmodel     e.g. warthoglin.bbmodel
```

The **model name inside BetterModel must match the boss id** (BetterModel usually names a model after
its file). If it doesn't, set `model: "<modelName>"` in the boss's `bosses/<id>.yml`. When a model
isn't found, WildBosses logs the list of models BetterModel actually loaded so you can pick the right
name.

## Recommended: one pack via Oraxen (`/wb assets setup`)

To serve a **single** pack through Oraxen (no double pack), let BetterModel write its assets straight
into Oraxen's pack folder. Run once:

```
/wb assets setup      # sets BetterModel to pack-type: folder, build-folder-location: plugins/Oraxen/pack,
                      # merge-with-external-resources: false  (a backup config.yml.wildbosses-backup is made)
```

Then, whenever you add/change a model:

```
/wb assets redeploy   # installs models/*.bbmodel into plugins/BetterModel/models/
/bettermodel reload   # BetterModel writes its models+textures into Oraxen/pack/assets
/oraxen reload        # Oraxen builds & serves the one pack (now containing the models)
```

`/wb assets status` shows the **Pack mode**: `folder → Oraxen (single pack) ✔` once setup is done.

### Alternative (without setup): zip → uploads

Without `/wb assets setup`, WildBosses copies BetterModel's built pack zip into `Oraxen/pack/uploads/`
so Oraxen merges it. This needs an extra step (the zip only exists after `/bettermodel reload`):
`/wb assets redeploy` → `/bettermodel reload` → `/wb assets redeploy` → `/oraxen reload`.

## Single pack, no fighting

With Oraxen serving the pack, BetterModel does not send its own pack (it has no resource-pack server),
so there is no double pack — everything the client downloads comes from Oraxen. The `setup` command
also turns off BetterModel's `merge-with-external-resources` so only Oraxen does the merging.

## Config

```yaml
integrations:
  oraxen:
    auto-deploy: true                              # install models + merge on startup
    bettermodel-pack: "pack/BetterModel-Pack.zip"  # (zip mode only) where BetterModel writes its pack,
                                                   # relative to the BetterModel folder; if not found
                                                   # there, the newest .zip under it is used.
```

## Troubleshooting "the model doesn't show"

- **`pack merged: false`** → BetterModel's pack wasn't found when you deployed. Run `/bettermodel
  reload` first, then `/wb assets redeploy` again, then `/oraxen reload`. If your BetterModel writes
  its pack somewhere else, set `bettermodel-pack` to that path.
- **Model invisible even after merge** → the model name likely doesn't match the boss id. Check the
  console line "Loaded models: [...]" and set `model:` in the boss file accordingly.
- **Two packs fighting** → with Oraxen serving the pack, disable BetterModel's own resource-pack
  sending (BetterModel `config.yml` → `resource-pack.server.enabled: false` / `resource-pack.enabled`
  as appropriate) so only Oraxen's merged pack is sent to players.
