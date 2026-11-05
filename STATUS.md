# Status

**Core test status:** the review reports 95 passing core tests on JDK 21. The project's target is Java 25, and the review did not run Gradle. The current review follow-up changes have not been built or tested locally.

**Loader build status:** the review did not build Fabric or NeoForge. Use GitHub Actions for current build verification; local builds are not part of this workflow.

**Not yet tested in game:** all screens, runtime hooks, mod-list config entrypoints, loaders and platforms. NeoForge has not been launched in a game client. See `TESTING.md` for the manual QA sequence.

**Known gaps**
- Opening the data folder uses Minecraft's own platform helper, found by reflection (`Util.getPlatform().openPath`). If it is not found on 26.3 the path is copied to the clipboard instead; check which happens and, once known, replace the reflection with a direct call.
- Credential cards and screens have no automated tests or in-game verification.
- Screen behavior, malformed-account repair, prompt queueing, and gated erase need in-game verification on both loaders. See `TESTING.md`.
