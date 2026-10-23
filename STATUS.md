# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — PARTIAL: project skeleton and build workflow files are present. The owner reports a successful build; the current Actions run compiled all projects, but the core test job failed, so M0 is not marked DONE.
- M1 — PARTIAL: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging are implemented with tests. The current CI run passed the M1 tests. Device providers are scheduled for M5. Full fault-injection, backup-rotation, concurrent-store, mode-switch and failure-path tests remain.
- M2 — PARTIAL: password generation (including a 1,024-entry memorable list), TOTP, host normalization, prompt/result matching, templates, and an initial session state machine are implemented with tests. The current CI run passed 29 of 30 core tests; the remaining failure was in the rejection-sampling test fake. Several required session transitions and behavior details remain incomplete.
- M3 — TODO: requires reading both API dump artifacts first. They are unavailable from this checkout.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- The current Actions run compiled core main and test sources. Fabric and NeoForge entrypoint declarations are unverified against API dumps and have not been launched in-game.
- No current implementation behavior has been verified in-game; M3 and later have not been implemented.

## CI

- Last commit: `befe166` (`feat: add password and login core`).
- CI status: failed on the owner's reported run for `befe166`; the core test task had 1 failure out of 30. The failing rejection-sampling test fake is corrected in the working tree, pending commit and CI rerun. GitHub CLI authentication is unavailable in this environment.
