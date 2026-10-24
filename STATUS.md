# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE (owner-reported CI): skeleton and build workflow. Last historical green report is documented below; no current Actions status is available from this workspace.
- M1 — DONE (owner-reported CI): encrypted vault format, AES-GCM, PBKDF2, JSON model, atomic writes, backups and merge. Follow-up now adds KDF bounds, tolerant JSON parsing, daily backup rotation and backup restore; this follow-up is unverified by CI.
- M2 — DONE (owner-reported CI): password generation, TOTP, host normalization, prompt/result matching, templates and login state machine. Follow-up fixes for rule precedence, prompt order, memorable-policy adjustment and single-pass substitution are unverified by CI.
- M3 — DONE (build, owner-reported): Fabric/NeoForge hooks, mixins, history scrubbing, keybinds, background work and vault access. Runtime behavior has not been verified in-game.
- M4 — DONE (build, owner-reported): credential cards, vault list/editor, settings, policy and templates. Current code-review follow-ups have not been verified by CI or in-game.
- M5 — DONE (build, owner-reported): master-password vault with optional trusted-device key file and master-password change screen. Trust remains enabled by default; new vault creation displays a chat notice explaining the local key and settings option. Windows ACL behavior and trusted unlock still need verification; current changes are unverified by CI.
- M6 — PARTIAL (workspace): encrypted import/export, merge, streamer mode, auto-lock, opt-in TOTP, daily backup rotation, and restore exist. Code-review hardening and regression tests are added but not yet run. Current workspace changes have not been built by CI; M6 is not complete.

## Code-review follow-up

Implemented in the current workspace, not yet CI-verified:

- Keep the live vault password intact during credential lookup by returning a temporary defensive account copy.
- Preserve and restore a previously verified password if replacement registration fails; discard the saved prior copy when registration succeeds.
- Prioritize success responses over incidental password-rule text, narrow the default rule matcher, prefer registration prompts when both prompt types match, and make dismissed-prompt suppression temporary.
- Bound accepted KDF work to the planned range; merge imported servers that share a host; refuse export onto existing files; tolerate missing/unknown JSON fields conservatively.
- Catch failures at Minecraft chat/command hook boundaries, reset failed session handling, and make command-template substitution single-pass.
- Handle memorable-password policies when server constraints are applied; accept grouped Base32 TOTP secrets; allow longer captured passwords.
- Remove common `/l` and `/log` aliases from built-in password detection, avoid firing keybinds while a screen is open, and stop resetting the auto-lock timer on ordinary gameplay keypresses.
- Bound and clear queued vault actions, isolate timer scheduling from vault I/O, remove canceled timers promptly, fail closed on unreadable settings for trusted unlock, validate setting strings, and allow a zero-millisecond autofill delay.
- Load the documented optional `patterns.json`; ignore malformed user pattern files without disabling built-in patterns.
- Filter vault search before pagination, preserve list state after edits, and omit server name/address from search matching in streamer mode. Show server-only policy rows so “Never save here” can be reversed. Keep registration/rejection prompts from timing out or closing on outside clicks.
- Reload encrypted state before each unlocked operation so another game process's atomic save becomes visible; bound queued prompts and clear them on disconnect.
- Document the at-rest threat model, trusted-device tradeoff, clipboard behavior, encrypted transfer, tombstones, and backup limitations.
- Distinguish incorrect master passwords from damaged vault contents; add regression coverage for restore, rotation, and master-password changes.

## Findings still open or requiring game verification

- Registration failure path, import/export overwrite protection, duplicate-host merge, KDF limits, and account preservation need unit/CI verification after these changes.
- Trusted-device mode stores a plaintext vault key beside the encrypted vault. This remains the owner's requested default; Windows relies on the profile ACL, which this code does not set or independently verify.
- Full screen lifecycle handling, cross-process write contention, server-policy merge precedence, Windows ACL enforcement, and recovery from impossible server password rules require further work.
- Mixin optionality and Minecraft's in-memory up-arrow history behavior remain unverified. Async callback failures still need a complete audit.
- Current API targets and all Minecraft-facing behavior remain unverified in-game. Modded-server prompt detection and AuthMe TOTP command syntax also need manual validation.
- Duplicate locale stubs remain because the plan explicitly lists them; they are English placeholders, not translations.

## Compiles but not verified in-game

- The owner previously reported M0-M5 and the loader integrations building successfully. Those reports do not verify the current uncommitted workspace changes.
- Unverified behavior includes login/register detection, autofill timing, command-history scrubbing, keybinds, dialogs, config screens, trusted unlock, import/export, TOTP, auto-lock and streamer mode on both loaders.
- Nothing is currently marked verified in-game.

## CI and repository state

- Last commit visible in this checkout: `5630162` (`feat: complete vault transfer and TOTP polish`).
- Current source and documentation edits have not been pushed and have no associated green Actions run. This environment has not verified CI status.
- Static rules, whitespace, locale parity, and JSON syntax checks passed locally. Regression tests have been added but have not yet run; the local Gradle invocation failed before project configuration because Gradle could not load `libnative-platform.so`. No green Actions run is available for these changes.
- API-dump generation is not part of ongoing CI. Loader API names must still be verified against real toolchain sources when new loader-facing work is needed.
