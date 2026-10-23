# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE: the skeleton and build workflow meet the planned layout. The owner reports the complete CI build passed on the current base commit `f189cec`.
- M1 — DONE: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging passed the reported CI build. Additional fault-injection and failure-path hardening remains for M6.
- M2 — DONE: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and the pure-Java session state machine passed CI on `f2d881a`. Tests cover registration, retries, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation.
- M3 — DONE (build): Fabric/NeoForge system chat and connection callbacks, command-tree and outgoing-command mixins, command-history scrubbing, event-based keybinds, lazy background work, vault access, and the minimal credential confirmation screen build on both loaders. API names/signatures were checked against refreshed dumps and local 26.3 bytecode, including `command_history.txt` persistence. The owner reports CI passed on `fdc9be2`. `NeoForgePlatform.isDevEnvironment()` returns false with a verification TODO because the exact NeoForge environment API was absent from the supplied dumps; current behavior does not use it. In-game behavior remains unverified.
- M4 — DONE (build): includes the save/update/register/rejected credential card, encrypted-vault account list and editor, per-server autofill/save policy, command templates, password generation, and settings on both loaders. The owner reports the latest CI run passed on `ec729a2`. Current follow-up changes restore dialog password controls, suppress repeated dismissed registration prompts, and add settings help tooltips; those changes still need CI and in-game verification.
- M5 — DONE (build): master-password-only vault with optional trusted-device auto-unlock using a local vault-key file. No OS keystores or OS commands. The owner reports CI passed on `ec729a2`; trusted unlock behavior needs in-game verification.
- M6 — TODO.

## Compiles but not verified in-game

- M0-M5 sources compiled in the owner-reported green Actions run on `ec729a2`.
- M3 API targets checked against dumps/bytecode but not verified in-game: system chat filtering, join/disconnect lifecycle, command tree signal, manual command capture, vanilla history scrubbing, keybind behavior, encrypted-vault prompts, and registration/autofill command flow. Multiplayer screen button is omitted.
- The NeoForge development-environment flag is also unverified and intentionally returns false pending a dump of the relevant loader API.
- M4 dialogs, settings screens, clipboard clearing, and template selection compiled in the owner-reported green Actions run but still need in-game verification on both loaders.
- M5 trusted-device auto-unlock, removing the trust file when disabled, password fallback, and the settings default need in-game verification.
- Current working changes restore Reveal password, Copy password, Another password, and Never save here actions; dismissing registration suppresses duplicate queued prompts; settings options show hover descriptions; vault pagination is clamped and layout adapts to window dimensions; password presets have descriptive labels; clipboard clear delay is five minutes. These changes have not yet compiled in CI or been tested in-game.
- Nothing has been verified in-game.

## CI

- Last owner-reported green commit: `ec729a2` (`fix: compact credential prompts and restore config controls`).
- Current follow-up workspace changes need a new GitHub Actions run.
- API-dump artifacts remain out of the workflow; the owner supplied the exact Mod Menu artifact needed for the Fabric integration.
