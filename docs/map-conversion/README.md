# BuildBattles map conversion record

The archive in `maps/legacy-buildbattles.zip` was recovered from the deployed
Nukkit `BuildBattle/default/worlds/game` template and converted on 2026-07-15.
The source files were checksummed before copying and were never used as an
in-place conversion target.

## Pipeline

1. Keep only source regions containing authored blocks or block entities.
2. Remap every observed Nukkit numeric ID and metadata through the fail-closed
   `correspondence-table.json` allowlist.
3. Normalize the ten legacy sign IDs and all 40 text lines to valid Java JSON
   components.
4. Upgrade copied chunks through Mojang 1.21.4 and Mojang 26.1.2 DataFixer.
5. Terminate each conversion server at optimizer completion, before world
   preparation can tick blocks or generate terrain.
6. Crop generated/empty chunks and audit Anvil sectors, palettes, coordinates,
   block counts, data versions, block entities, and embedded entities.

## Result

| Check | Result |
| --- | ---: |
| Source chunks inspected | 784 |
| Authored chunks retained | 72 |
| Non-air blocks before / after | 62,939 / 62,939 |
| Signs before / after | 10 / 10 |
| Embedded entities after | 0 |
| Chunk DataVersion | 4790 |
| Bounds | x 40..202, y 3..32, z 52..172 |

Archive SHA-256:
`156d8e03c960a20a3967531c40012829beafa9f1eca17ba66dbbd719973e69e6`.

The full machine-readable evidence is in
`legacy-buildbattles.remap.json` and `legacy-buildbattles.audit.json`.
