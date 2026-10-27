# Design rules

These rules apply to every change.

**Security**
- No network access of any kind, no external processes, no native code. `scripts/check-rules.sh` greps for the obvious cases.
- Secrets are `char[]` or `byte[]` and are wiped when no longer needed. Never log a password, key, master password or vault content; log exception class names only.
- Anything read from a server (chat text, command trees) is untrusted: cap its length, match it under a time budget, and never let it delete data.
- Writes are atomic (temp file, fsync, rename) and files are owner-only where the platform allows it.

**Minecraft integration**
- Keep game-version-specific code in `shared-client` and loader APIs in `fabric` and `neoforge`. Everything else stays in `core` so it can be unit-tested without the game.
- At most three small mixins, all with `require = 0` so a Minecraft update cannot crash the game. Because that makes failures silent, privacy-critical hooks are self-checked at runtime and warn the owner when inactive.
- No per-tick work. Game-thread code never does file or key-derivation work; use `BackgroundExecutor`.
- The jar stays under 256 KiB.

**Style**
- Java 25, no external dependencies beyond what the game and loaders provide.
- Small, single-purpose classes; comments say why, not what.

**Out of scope**
Server-side plugins, cloud sync, browser import, accounts or telemetry, and an OS keychain.

**Verify on every Minecraft update**
Mixin targets (`ClientPacketListener.sendCommand`, `CommandHistory.addCommand`, `KeyboardHandler.keyPress`), the screen and widget APIs used by the screens, `KeyEvent.modifiers()`, and both loaders' event and client-command APIs.
