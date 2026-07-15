# BuildBattles map assets

Versioned, converted Paper world archives belong in this directory. The legacy
arena filename is `legacy-buildbattles.zip`; its `level.dat` and `region/`
directory must be at the zip root.

Run `./gradlew :BuildBattles:installMaps` from the `Cookies` repository to copy
the archives into the configured Paper server.
