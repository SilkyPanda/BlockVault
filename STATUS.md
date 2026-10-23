# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE: the skeleton and build workflow meet the planned layout. The owner reports the complete CI build passed on the current base commit `f189cec`.
- M1 — DONE: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging passed the reported CI build. Device providers are scheduled for M5. Additional fault-injection and failure-path hardening remains for M6.
- M2 — DONE: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and the pure-Java session state machine passed CI on `f2d881a`. Tests cover registration, retries, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation.
- M3 — DONE (build): Fabric/NeoForge system chat and connection callbacks, command-tree and outgoing-command mixins, command-history scrubbing, event-based keybinds, lazy background work, vault access, and the minimal credential confirmation screen build on both loaders. API names/signatures were checked against refreshed dumps and local 26.3 bytecode, including `command_history.txt` persistence. The owner reports CI passed on `fdc9be2`. `NeoForgePlatform.isDevEnvironment()` returns false with a verification TODO because the exact NeoForge environment API was absent from the supplied per-class dumps; current behavior does not use it. In-game behavior remains unverified.
- M4 — PARTIAL (implementation in the workspace; not yet compiled by CI): added the save/update/register/rejected credential card, encrypted-vault account list and editor, per-server autofill/save policy, built-in command-template selection, password generation, settings using vanilla `OptionsSubScreen`/`OptionsList`, matching locale stubs, and original card/icon textures. Device key mode remains disabled until M5. The server-specific icon is not rendered; the bundled BlockVault icon is used. Entrance animation and the card's combined "more" menu are not implemented. Need a green CI run before calling this DONE.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- M0-M3 sources compiled in the owner-reported green Actions run through `fdc9be2`.
- M3 API targets checked against dumps/bytecode but not verified in-game: system chat filtering, join/disconnect lifecycle, command tree signal, manual command capture, vanilla history scrubbing, keybind behavior, encrypted-vault prompts, and registration/autofill command flow. Multiplayer screen button is omitted.
- The NeoForge development-environment flag is also unverified and intentionally returns false pending a dump of the relevant loader API.
- M4 UI source is in the workspace but has no CI compilation result yet. Once CI is green, the credential card, vault list/editor, settings, clipboard clearing, and template selection still need in-game verification on both loaders.
- Nothing has been verified in-game.

## CI

- Last owner-reported green commit: `fdc9be2` (`fix: handle unsupported vault versions`).
- CI status: the owner reports both loader builds passed on `fdc9be2`. M4 changes are uncommitted/unpushed and have not been compiled in Actions. Local Gradle cannot start because it fails to load `libnative-platform.so`.
