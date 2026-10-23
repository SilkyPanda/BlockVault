# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — PARTIAL: project skeleton and build workflow files are present. The supplied core report is green, but the complete current Actions run has not been inspected, so M0 is not marked DONE.
- M1 — PARTIAL: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging are implemented with tests. The supplied core report passed the M1 tests. Device providers are scheduled for M5. Full fault-injection, backup-rotation, concurrent-store, mode-switch and failure-path tests remain.
- M2 — PARTIAL: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and an expanded pure-Java session state machine are implemented. Tests now cover registration ordering/success, failure, one rule retry, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation. The updated tests have not run in CI yet; local Gradle cannot initialize `libnative-platform.so` in this environment.
- M3 — TODO: refreshed API dumps have been read. They confirm Fabric `KeyMappingHelper.registerKeyMapping(KeyMapping)`, GAME and Join/Disconnect callback signatures; Minecraft GUI/client/network APIs; NeoForge system-chat, client connection, key registration, event bus, and nested screen event APIs. Cross-loader class-not-found entries are expected. No loader hooks have been implemented yet.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- The supplied core report shows core main and test sources compiled. Current M2 changes have not been compiled by CI. M3 hooks are not implemented yet; existing Fabric and NeoForge entrypoint declarations have not been verified against complete API dumps or launched in-game.
- No current implementation behavior has been verified in-game; M3 and later have not been implemented.

## CI

- Last inspected code commit: `9bb3a2d` (`feat: complete core session logic`).
- CI status: the owner-provided Actions log for `9bb3a2d` reports 40 of 41 tests passed; `LoginSessionTest.registerFailureDeletesPendingCredential` failed because `Registration failed!` was not classified. The default failure pattern and a direct matcher assertion are updated in the current worktree; this fix has not been pushed or run in CI. Local `gradle --no-daemon test` cannot start because Gradle fails to load `libnative-platform.so`.
