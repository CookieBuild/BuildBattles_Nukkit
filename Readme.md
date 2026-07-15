# Cookie Build BuildBattles

Paper 26 reimplementation of `CookieBuild/BuildBattles_Nukkit`, integrated with
CookieDough queues, parties, progression, match statistics and replay.

## Required map

The recovered, converted immutable world template is versioned in this repository
at:

`maps/legacy-buildbattles.zip`

The zip must contain `level.dat` and region `.mca` files at its root. A nested,
empty, corrupt, or coordinate-mismatched template is rejected and no game is
registered, so the lobby cannot offer an empty arena.

The eight plot centers and the deployed `plot_size: 13`, `plot_up: 20`, and
`plot_down: 2` geometry are preserved in `config.yml`. The recovered game world
has no dedicated waiting platform because the Nukkit game waited in a separate
lobby world, so the Paper implementation safely reuses the first plot until the
match begins. The judging camera preserves the legacy center-plus-six-block
offset.

Install the versioned archive into the sibling Paper 26 test server with:

```sh
./gradlew :BuildBattles:installMaps
```

The default destination is `../Paper26_Test_Server/buildbattles_maps` relative
to the `Cookies` checkout. Override it for another server with an
absolute or `Cookies`-root-relative path:

```sh
./gradlew :BuildBattles:installMaps \
  -PcookiebuildBuildBattlesMapInstallDir=/path/to/server/buildbattles_maps
```

## Validation

From `Cookies`:

```sh
./gradlew :BuildBattles:test :BuildBattles:build
```
