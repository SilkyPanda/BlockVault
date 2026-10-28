# Status

**Verified by tests:** the `core` library (crypto, vault storage, trash, merging, import preview, backups, detection, generator, login session) with 91 passing unit tests.

**Builds verified:** Fabric and NeoForge both build on Java 25. Their jars are 232.03 KiB and 229.68 KiB, below the 256 KiB limit.

**Not yet tested in game:** all screens, runtime hooks, mod-list config entrypoints, loaders and platforms. NeoForge has not been launched in a game client. See `TESTING.md` for the manual QA sequence.

**Known gaps**
- Opening the data folder uses Minecraft's own platform helper, found by reflection (`Util.getPlatform().openPath`). If it is not found on 26.3 the path is copied to the clipboard instead; check which happens and, once known, replace the reflection with a direct call.
- Credential cards and screens have no automated tests or in-game verification.
- The Gradle wrapper is pinned to 9.7.1 without a distribution checksum; add one with `./gradlew wrapper --gradle-version 9.7.1 --gradle-distribution-sha256-sum <hash>`.
