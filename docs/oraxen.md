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

## The flow

```
/wb assets redeploy   # installs models/*.bbmodel into plugins/BetterModel/models/
/bettermodel reload   # BetterModel loads the models and BUILDS its pack (BetterModel-Pack.zip)
/wb assets redeploy   # copies the freshly-built pack into Oraxen/pack/uploads/bettermodel.zip
/oraxen reload        # Oraxen merges pack/uploads/*.zip and re-serves the pack
```

Why twice? BetterModel's pack zip only exists **after** `/bettermodel reload`. The first redeploy
installs the models; the second (after the reload) grabs the built pack. `/wb assets redeploy` tells
you exactly which step is still missing (e.g. "BetterModel's pack isn't built yet").

`/wb assets status` shows whether BetterModel/Oraxen are present, how many `.bbmodel` you have, and
whether BetterModel has built its pack yet.

## Config

```yaml
integrations:
  oraxen:
    auto-deploy: true                              # deploy on startup
    bettermodel-pack: "pack/BetterModel-Pack.zip"  # where BetterModel writes its pack (relative to
                                                   # the BetterModel folder). If not found there,
                                                   # the newest .zip under the BetterModel folder is used.
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
