# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — DONE: the skeleton and build workflow meet the planned layout. The owner reports the complete CI build passed on the current base commit `f189cec`.
- M1 — DONE: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging passed the reported CI build. Device providers are scheduled for M5. Additional fault-injection and failure-path hardening remains for M6.
- M2 — DONE: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and the pure-Java session state machine passed CI on `f2d881a`. Tests cover registration, retries, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation.
- M3 — PARTIAL: implementations now cover Fabric/NeoForge system chat and connection callbacks, command-tree and outgoing-command mixins, command-history scrubbing, event-based keybinds, lazy background work, vault access, and a minimal credential confirmation screen. API names/signatures were checked against the supplied refreshed dumps and local 26.3 bytecode, including the `command_history.txt` persistence path. The first M3 CI attempt (`6ba7793`) failed because direct GLFW imports were absent from both loader compile classpaths; the fix now uses Minecraft's verified `InputConstants.UNKNOWN` and the press action value consumed by `KeyboardHandler`. `NeoForgePlatform.isDevEnvironment()` currently returns false with a verification TODO because the exact NeoForge environment API was absent from the supplied per-class dumps; this method is not used by current behavior. The fix still needs CI.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- M0-M2 sources compiled in the owner-reported green Actions run through `f2d881a`. Current M3 fix has not been compiled by CI.
- M3 API targets checked against dumps/bytecode but not verified in-game: system chat filtering, join/disconnect lifecycle, command tree signal, manual command capture, vanilla history scrubbing, keybind behavior, encrypted-vault prompts, and registration/autofill command flow. Multiplayer screen button is omitted.
- The NeoForge development-environment flag is also unverified and intentionally returns false pending a dump of the relevant loader API.
- Nothing has been verified in-game.

## CI

- Last owner-reported green commit: `f2d881a` (`fix: wipe captured credential staging arrays`).
- CI status: the owner reports M2 passed on `f2d881a`; M3 commit `6ba7793` failed in both loaders due to missing `org.lwjgl.glfw` compile dependencies. The dependency-free key input fix is pending a CI run. Local Gradle cannot start because it fails to load `libnative-platform.so`.
