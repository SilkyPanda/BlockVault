# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — PARTIAL: project skeleton and build workflow files are present. The supplied core report is green, but the complete current Actions run has not been inspected, so M0 is not marked DONE.
- M1 — PARTIAL: AES-GCM, PBKDF2, bounded BVLT parsing, JSON models, data directory resolution, master-password wrapping, atomic writes, backups, and snapshot merging are implemented with tests. The supplied core report passed the M1 tests. Device providers are scheduled for M5. Full fault-injection, backup-rotation, concurrent-store, mode-switch and failure-path tests remain.
- M2 — PARTIAL: password generation (including a 1,024-entry memorable list), TOTP, host normalization, pattern matching, prompt-derived templates, per-server policy, and an expanded pure-Java session state machine are implemented. Tests now cover registration ordering/success, failure, one rule retry, pending recovery, autofill timing/rejection/timeout, manual capture, policy suppression and disconnect cancellation. The updated tests have not run in CI yet; local Gradle cannot initialize `libnative-platform.so` in this environment.
- M3 — TODO: updated API dumps have now been read. The Fabric key mapping helper is `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper` (the old `keybinding.v1` target is absent). The dumps confirm the Minecraft client/network/chat APIs, Fabric GAME and Join/Disconnect callback signatures, and NeoForge event class names via the class index. NeoForge nested connection/screen event signatures and the corrected Fabric key helper signature are queued for the next CI API dump before loader code uses them.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- The supplied core report shows core main and test sources compiled. Current M2 changes have not been compiled by CI. M3 hooks are not implemented yet; existing Fabric and NeoForge entrypoint declarations have not been verified against complete API dumps or launched in-game.
- No current implementation behavior has been verified in-game; M3 and later have not been implemented.

## CI

- Last inspected code commit: `0060d6b` (`ci: expand loader API diagnostics`).
- CI status: user-provided prior report shows the core suite passed at an earlier commit; the updated dump artifacts were read from the workspace. No Actions result for the current uncommitted changes is available. Local `gradle --no-daemon test` could not start because Gradle failed to load `libnative-platform.so`. GitHub CLI authentication is unavailable in this environment.
