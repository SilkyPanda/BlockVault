# Implementation status

Updated: 2026-10-24

## Milestones

- M0 — DONE (owner-reported CI): project skeleton and GitHub Actions build workflow.
- M1 — DONE (owner-reported CI): pure-Java encrypted vault core, serialization, backups, and merge/import support. Current changes to explicit-write conflict handling, damaged-vault recovery, and backup re-encryption have not run in CI.
- M2 — DONE (owner-reported CI): password generation, host handling, prompt/result matching, command templates, and login session. New review-driven regressions and fixes are not CI-verified.
- M3 — DONE (owner-reported build): Fabric and NeoForge hooks, mixins, background work, and vault access. Current removal of command-tree handling is not CI-verified.
- M4 — DONE (owner-reported build): credential cards, vault list/editor, settings, and policies. Current prompt suppression, search focus, reauthentication removal, and recovery UI changes are not CI-verified or game-tested.
- M5 — DONE (owner-reported build): master-password vault, trusted-device option, and password-change screen. Current 8-character minimum, same-password rejection, and backup re-encryption are not CI-verified.
- M6 — PARTIAL: import/export, streamer mode, auto-lock, backups, inspection/restore, and regression coverage exist. Current review fixes are pending CI and in-game checks.

## Review round 2 work in this workspace

Implemented but not yet verified by CI:

- Fixed success-message negation matching and broadened password-rule detection, with core regression tests.
- Resolve messages mentioning both login and registration against the saved account status.
- Use explicit local vault writes with an encrypted-file digest conflict check, so account rollback and server deletion persist without silently overwriting another process's edits.
- Added damaged/missing-main-vault recovery using the password that encrypted the selected backup; separated ordinary file I/O errors from corruption.
- Re-encrypt all automatic and manual backups on master-password change; enforce a minimum of 8 characters and reject reusing the current password.
- Remove password reauthentication from editing and reveal flows after the vault is unlocked; update documentation to state this access model.
- Let Trust silently unlock after inactivity auto-lock during the same session, while auto-lock closes sensitive screens; manual Lock now remains locked pending password entry.
- Registration Cancel suppresses the matching prompt until disconnect. Never save here is available on registration and remains a per-server preference.
- Malformed user pattern structures and regexes are skipped while built-in detection stays active; each custom regex is limited to 256 characters.
- Keep search focus by updating visible row controls without rebuilding the screen on each keystroke.
- Refresh the backup list after restore, clear revealed backup passwords on screen removal, avoid deleting trust keys after a config read error, and prevent export cleanup from deleting a pre-existing path.
- Queue the first-use trust notice when a vault is created outside a world; wipe prompt secrets on screen removal without replacing a disconnect screen; lock vault snapshot reads and avoid decrypting unchanged files during reload.
- Remove non-English English-placeholder locale files, command-tree handling, obsolete reauthentication methods, and several confirmed dead helpers/types.
- Adjust memorable-password word count to fit server length rules; legacy `authme` entries now use automatic prompt detection; skip automatic backups for vaults with no accounts; enforce a 200 ms autofill-delay minimum.
- Add settings actions to open the BlockVault data directory and erase its contents after a warning and master-password verification; this does not uninstall the launcher-managed mod jar.
- The Vault action in settings now continues to the vault list after first-time creation or master-password unlock. This navigation fix is not yet verified by CI or in-game testing.
- Opening the data folder now tries Java's open and browse desktop actions, then copies the resolved path to the clipboard if both fail. Windows behavior remains unverified by CI and in-game testing.

## TODO / unresolved review work

- Fabric has no verified direct vault-entry route when neither a keybind nor Mod Menu is available. The current API dump artifacts are absent, so a multiplayer-screen button cannot be added without guessing loader/Minecraft APIs.
- Decide how to handle unknown vault JSON fields across mod versions; edits currently discard fields outside the current schema. This is awaiting owner direction because preserving fields can retain unsupported data from old builds.
- Remaining review checks include loader entry-point behavior and in-game lifecycle behavior; all current code changes await CI and game verification.

## In-game reports

- Fabric: the owner reported checks 1–7 and 10–13 passed; checks 8, 9, and 14 remain unchecked. Those results predate the current workspace changes.
- NeoForge: the owner reported no checks completed yet. No current changes are verified in-game on either loader.

## Compiles but not verified in-game

- The owner reported earlier builds succeeding on both loaders. This does not verify the current workspace, which has not been built by CI.
- Recovery from a missing/damaged main vault, backup password re-encryption, trusted re-unlock after inactivity, registration suppression, backup-list refresh, prompt matching, the settings Vault navigation fix, and all current screen changes remain unverified in-game. No Windows-specific build or runtime check has verified the reported missing registration card.

## CI and repository state

- Last commit visible in this checkout: `5630162` (`feat: complete vault transfer and login polish`).
- The current working-tree changes have no associated GitHub Actions run. No current CI status is available to this workspace, and no build claim is made for these changes.
- Local builds are not authoritative; use GitHub Actions as the source of truth. API dump artifacts are not present in this checkout, so no new loader APIs were guessed or added.
