# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE (owner-reported CI): skeleton and build workflow. Last historical green report is documented below; no current Actions status is available from this workspace.
- M1 — DONE (owner-reported CI): encrypted vault format, AES-GCM, PBKDF2, JSON model, atomic writes, backups and merge. Follow-up added KDF bounds, strict current-schema JSON parsing, daily backup rotation and backup restore; current manual-backup changes are unverified by CI.
- M2 — DONE (owner-reported CI): password generation, host normalization, prompt/result matching, templates and login state machine. The owner-provided latest core CI log had failures in command-tree arming and the second rule-retry state; both have been corrected in the workspace but need a rerun.
- M3 — DONE (build, owner-reported): Fabric/NeoForge hooks, mixins, history scrubbing, keybinds, background work and vault access. Runtime behavior has not been verified in-game.
- M4 — DONE (build, owner-reported): credential cards, vault list/editor, settings, policy and templates. Current code-review follow-ups have not been verified by CI or in-game.
- M5 — DONE (build, owner-reported): master-password vault with optional trusted-device key file and master-password change screen. Trust remains enabled by default; new vault creation displays a chat notice explaining the local key and settings option. Windows ACL behavior and trusted unlock still need verification; current changes are unverified by CI.
- M6 — PARTIAL (workspace): encrypted import/export, merge, streamer mode, auto-lock, three daily automatic backups, ten manual backups, backup inspection/restore, and regression coverage exist. Current workspace changes have not been built by CI; M6 is not complete.

## Current follow-up: pending login recovery and UI/settings

Implemented in the current workspace; not yet verified by CI:

- Replaced the raw account-state enum in the vault list with localized “Pending — not yet confirmed” and “Verified” labels.
- A successful manual login using the same password as a PENDING entry promotes that entry to VERIFIED, making it eligible for later automatic login. Added core regression coverage for this recovery path.
- A successful automatic login sends a localized green `[BlockVault] Logged in automatically.` chat message; added a core regression test for success-only notification.
- Removed the separate Safety Override / Disable auto-login preference and all visible locale entries. Existing global and per-server autofill controls remain.
- Clarified the master-password reauthentication screen as unlocking the selected saved entry and corrected its button alignment. Simplified the credential editor to password-only editing.
- Rewrote the first-vault trust notice with a colored BlockVault sender and the local vault-key/access implications. Added missing saved-status and auto-login chat translations to every locale file.
- Updated plan, decision, README and test-status notes for pending-state recovery, setting removal, and the current reported test coverage.

Owner-reported in-game status: Fabric checks 1–7 and 10–13 passed; checks 8–9 and 14 remain unchecked. NeoForge checks are all unchecked. The current workspace edits have not yet been built or tested in-game on either loader.

## Current follow-up: deletion crash and backup guidance

Implemented in the current workspace; not yet verified by CI or in-game:

- Fixed vault-list rendering when deleting the last account leaves a server-settings-only row. These rows have no account status; rendering now checks the row type before reading status and has a fallback for unexpected missing status.
- Expanded the recovery screen and README explanation: backups are encrypted full-vault snapshots; ordinary saves create at most one per 24 hours and retain three; a restore first captures the current vault so it can be undone. The schedule is triggered by saves rather than a timer.
- Added ten separate manual encrypted backup slots, backup type and creation timestamps in the list, and a details screen for inspecting saved accounts and revealing passwords while unlocked. Manual rotation leaves the three automatic slots untouched.
- Backup rows and details show encrypted file size and the details view reports saved-login and server counts. A selected password remains hidden until its Reveal button is pressed.
- The owner reported the list crash while deleting a password and a subsequent apparent freeze while reopening the vault. Verify both actions after updating; these code changes have not been built by CI.

## Code-review follow-up

Implemented in the current workspace, not yet CI-verified:

- Keep the live vault password intact during credential lookup by returning a temporary defensive account copy.
- Preserve and restore a previously verified password if replacement registration fails; discard the saved prior copy when registration succeeds.
- Prioritize success responses over incidental password-rule text, narrow the default rule matcher, prefer registration prompts when both prompt types match, and make dismissed-prompt suppression temporary.
- Bound accepted KDF work to the planned range; merge imported servers that share a host; refuse export onto existing files; parse the current vault schema strictly without preserving unknown fields.
- Catch failures at Minecraft chat/command hook boundaries, reset failed session handling, and make command-template substitution single-pass.
- Handle memorable-password policies when server constraints are applied; allow longer captured passwords.
- Remove common `/l` and `/log` aliases from built-in password detection, avoid firing keybinds while a screen is open, and stop resetting the auto-lock timer on ordinary gameplay keypresses.
- Bound and clear queued vault actions, isolate timer scheduling from vault I/O, remove canceled timers promptly, fail closed on unreadable settings for trusted unlock, validate setting strings, and allow a zero-millisecond autofill delay.
- Load the documented optional `patterns.json`; ignore malformed user pattern files without disabling built-in patterns.
- Filter vault search before pagination, preserve list state after edits, and omit server name/address from search matching in streamer mode. Show server-only policy rows so “Never save here” can be reversed. Keep registration/rejection prompts from timing out or closing on outside clicks.
- Reload encrypted state before each unlocked operation so another game process's atomic save becomes visible; bound queued prompts and clear them on disconnect.
- Document the at-rest threat model, trusted-device tradeoff, clipboard behavior, encrypted transfer, tombstones, and backup limitations.
- Distinguish incorrect master passwords from damaged vault contents; add regression coverage for restore, rotation, and master-password changes.

## Findings still open or requiring game verification

- Registration rollback, import/export overwrite protection, duplicate-host merge, KDF limits, account preservation, command-tree prompt behavior, rule retry, and backup restore need unit/CI verification after these fixes.
- Trusted-device mode stores a plaintext vault key beside the encrypted vault. This remains the owner's requested default; Windows relies on the profile ACL, which this code does not set or independently verify.
- Full screen lifecycle handling, cross-process write contention, server-policy merge precedence, Windows ACL enforcement, and recovery from impossible server password rules require further work.
- Mixin optionality and Minecraft's in-memory up-arrow history behavior remain unverified. Async callback failures still need a complete audit.
- Current API targets and all Minecraft-facing behavior remain unverified in-game. Modded-server prompt detection also needs manual validation.
- Duplicate locale stubs remain because the plan explicitly lists them; they are English placeholders, not translations.

## Compiles but not verified in-game

- The owner previously reported M0-M5 and the loader integrations building successfully. Those reports do not verify the current uncommitted workspace changes.
- Unverified behavior includes login/register detection, autofill timing, command-history scrubbing, keybinds, dialogs, config screens, trusted unlock, import/export, auto-lock and streamer mode on both loaders.
- Nothing is currently marked verified in-game.

## CI and repository state

- Last commit visible in this checkout: `5630162` (`feat: complete vault transfer and login polish`).
- Current source and documentation edits have not been pushed and have no associated green Actions run. This environment has not verified CI status.
- Static rules, whitespace, locale parity, and JSON syntax checks passed locally. The owner-provided Actions run reported 50 core tests with three failures; the command-tree, rule-retry, and backup-recovery issues have since been fixed but not rerun. The local Gradle invocation failed before project configuration because Gradle could not load `libnative-platform.so`. No green Actions run is available for these changes.
- API-dump generation is not part of ongoing CI. Loader API names must still be verified against real toolchain sources when new loader-facing work is needed.
