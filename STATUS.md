# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE: the skeleton and build workflow meet the planned layout. The owner reports the complete CI build passed on the current base commit `f189cec`.
- M1 — DONE: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging passed the reported CI build. Additional fault-injection and failure-path hardening remains for M6.
- M2 — DONE: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and the pure-Java session state machine passed CI on `f2d881a`. Tests cover registration, retries, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation.
- M3 — DONE (build): Fabric/NeoForge system chat and connection callbacks, command-tree and outgoing-command mixins, command-history scrubbing, event-based keybinds, lazy background work, vault access, and the minimal credential confirmation screen build on both loaders. API names/signatures were checked against refreshed dumps and local 26.3 bytecode, including `command_history.txt` persistence. The owner reports CI passed on `fdc9be2`. `NeoForgePlatform.isDevEnvironment()` returns false with a verification TODO because the exact NeoForge environment API was absent from the supplied dumps; current behavior does not use it. In-game behavior remains unverified.
- M4 — DONE (build): the owner reports CI passed on `be0f292`. Added the save/update/register/rejected credential card, encrypted-vault account list and editor, per-server autofill/save policy, built-in command-template selection, password generation, vanilla `OptionsSubScreen`/`OptionsList` settings, and locale strings. The latest working UI removes the card textures and excess actions for compactness. In-game behavior is unverified.
- M5 — PARTIAL (workspace implementation; CI pending): removed generic device-key mode/provider scaffolding; added master-password-only BVLT validation, a default-on `Trust this device` / `Ask for master password every launch` setting, and an owner-only local vault-key file for trusted auto-unlock. No OS keystores or OS commands. Core tests cover trusted unlock and invalid-key fallback. Need CI green and in-game verification.
- M6 — TODO.

## Compiles but not verified in-game

- M0-M3 sources compiled in the owner-reported green Actions run through `fdc9be2`.
- M3 API targets checked against dumps/bytecode but not verified in-game: system chat filtering, join/disconnect lifecycle, command tree signal, manual command capture, vanilla history scrubbing, keybind behavior, encrypted-vault prompts, and registration/autofill command flow. Multiplayer screen button is omitted.
- The NeoForge development-environment flag is also unverified and intentionally returns false pending a dump of the relevant loader API.
- M4 credential card, vault list/editor, settings, clipboard clearing, and template selection compiled in the owner-reported green Actions run but still need in-game verification on both loaders.
- M5 changes are not yet compiled by CI. Trusted-device auto-unlock, deleting the trust file when disabled, password fallback, and the settings default need in-game verification after CI passes.
- Commit `4f3ec79` fixes credential-card texture endpoints and suppresses duplicate registration prompts; CI/build and in-game verification are pending.
- Commit `53071a9` adds `disableAutoLogin` and a NeoForge config-screen factory. The owner reports its CI failed because NeoForge could not disambiguate the `registerExtensionPoint` overload; that call is now explicitly typed. CI has not rerun this correction.
- Current working changes add Fabric Mod Menu 21.0.0 using the owner-provided Modrinth coordinate and config entrypoint, with plain `implementation` because Loom 1.18.2 lacks `modCompileOnly` and the plan specifies plain implementation for unobfuscated Minecraft 26.x. They also contain the explicit NeoForge factory typing fix. These changes need CI.
- New working changes make credential prompts area-limited and compact, remove their bundled image assets and secondary actions, and defer `OptionsSubScreen` initialization until settings load so its layout is built once. Storage paths and manual cleanup are now documented. These latest UI changes have not been compiled by CI or tested in-game.
- Nothing has been verified in-game.

## CI

- Last owner-reported green commit: `be0f292` (`feat: add vault screens and settings`).
- Latest commit reported by the owner: `72dd520` (`fix: register config screens on both mod loaders`); CI failed because `modCompileOnly` does not exist in Loom 1.18.2. That dependency is now plain `implementation` per the plan; a new CI run is needed. The NeoForge overload typing fix is also included in the pending changes.
- API-dump artifacts remain out of the workflow; the owner supplied the exact Mod Menu artifact needed for the Fabric integration.
