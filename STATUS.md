# Implementation status

Updated: 2026-10-23

## Milestones

- M0 — PARTIAL: project skeleton and build workflow files are present. Not committed or pushed; this checkout has no Git metadata and GitHub CLI is unavailable. CI has not run, so the milestone is not DONE.
- M1 — TODO: encrypted core and vault storage are not implemented.
- M2 — TODO: password generation, TOTP, host handling, pattern matching, and session logic are not implemented.
- M3 — TODO: requires green M0 CI and reading both API dump artifacts first.
- M4 — TODO.
- M5 — TODO.
- M6 — TODO.

## Compiles but not verified in-game

- No compilation has been verified by CI. The Fabric and NeoForge entrypoint declarations are unverified against the actual Minecraft 26.3 toolchain and have not been launched in-game.

## CI

- Last commit: unavailable (the supplied `.git` directory is empty/read-only).
- CI status: not observed; `gh` is unavailable and no push can be made from this checkout.
