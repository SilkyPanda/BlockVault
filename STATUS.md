# Status

**Verified by tests:** the `core` library (crypto, vault storage, trash, merging, import preview, backups, detection, generator, login session) with 90 unit tests.

**Written but never compiled or run:** everything under `shared-client`, `fabric` and `neoforge` (screens, runtime, mixins, loader hooks, commands). Compile it first; see `TESTING.md` for the order of work and the most likely trouble spots.

**Not yet tested in game:** all loaders and platforms. NeoForge has never been run.

**Known gaps**
- Opening the data folder uses Minecraft's own platform helper, found by reflection (`Util.getPlatform().openPath`). If it is not found on 26.3 the path is copied to the clipboard instead; check which happens and, once known, replace the reflection with a direct call.
- Credential cards and screens have no automated tests.
- The Gradle wrapper is pinned to 9.7.1 without a distribution checksum; add one with `./gradlew wrapper --gradle-version 9.7.1 --gradle-distribution-sha256-sum <hash>`.
