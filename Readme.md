# Cookie Build BuildBattles

Paper 26 reimplementation of `CookieBuild/BuildBattles_Nukkit`, integrated with
CookieDough queues, parties, progression, match statistics and replay.

## Required map

The historical repository contains plot coordinates but no world assets. Place
the recovered immutable world template at:

`buildbattles_maps/legacy-buildbattles.zip`

The zip must contain `level.dat` and region `.mca` files at its root. A nested,
empty, corrupt, or coordinate-mismatched template is rejected and no game is
registered, so the lobby cannot offer an empty arena.

The eight plot centers are preserved in `config.yml`. The waiting position is
inferred and must be visually checked against the recovered map.
The legacy `plot_size: 25` regions overlap because neighboring plot centers are
only 41-43 blocks apart. The placeholder uses a non-overlapping half-size of 19;
inspect the map borders before increasing it. The judging camera preserves the
legacy center-plus-six-block offset.

## Validation

From `Cookies`:

```sh
./gradlew :BuildBattles:test :BuildBattles:build
```
