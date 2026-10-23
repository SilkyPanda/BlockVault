# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — PARTIAL: project skeleton and build workflow files are present. The supplied core report is green, but the complete current Actions run has not been inspected, so M0 is not marked DONE.
- M1 — PARTIAL: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging are implemented with tests. The supplied core report passed the M1 tests. Device providers are scheduled for M5. Full fault-injection, backup-rotation, concurrent-store, mode-switch and failure-path tests remain.
- M2 — PARTIAL: password generation (including a 1,024-entry memorable list), TOTP, host normalization, prompt/result matching, templates, and an initial session state machine are implemented with tests. The supplied core report passed all 30 tests after fixing the test fake. Several required session transitions and behavior details remain incomplete.
- M3 — TODO: refreshed dumps verify Minecraft instance/server/user access, system chat packet handling, command history insertion, client command sending, Brigadier command roots, Fabric system-message and connection events, and NeoForge system-message and key-registration methods. Fabric `KeyBindingHelper` is unresolved and NeoForge nested connection/screen event signatures are not yet dumped; CI class-index and nested-signature coverage is being expanded before implementation.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- The supplied core report shows core main and test sources compiled. M3 hooks are not implemented yet; existing Fabric and NeoForge entrypoint declarations have not been verified against complete API dumps or launched in-game.
- No current implementation behavior has been verified in-game; M3 and later have not been implemented.

## CI

- Last inspected commit: `78ed56b` (`ci: refresh actions and repair api dumps`).
- CI status: the supplied report for `b162b82` shows 30 core tests passed and 0 failed. The supplied dumps for `78ed56b` contain valid signatures but incomplete loader class indexes. The complete Actions result was not included. Expanded indexes and nested signatures are pending commit and CI rerun. GitHub CLI authentication is unavailable in this environment.
