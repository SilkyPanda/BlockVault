# BlockVault

BlockVault is a client-side password manager for Minecraft servers with login plugins. It stores server credentials in an encrypted local vault and does not connect to external services.

Copyright © 2026 SilkyPanda. BlockVault is free software licensed under the GNU General Public License, version 3 only. See [LICENSE](LICENSE).

## Use

- Open or lock the vault from Mod Menu on Fabric or the mod config screen.
- On a recognized register prompt, review the generated password and confirm before BlockVault sends it. Verified saved logins can autofill on later joins. Pending logins require confirmation.
- Manage saved accounts, server login settings, backups, import/export, and vault security from the vault and settings screens. YACL is optional; a native settings screen is used when it is unavailable.
- When enabled, bare `/login` sends the verified saved password to the connected server. This is a client-side shortcut for the server's `/login` command.
- Add extra prompt, result, or command-name rules in `patterns.json` in BlockVault's data folder. Follow the JSON structure in `core/src/main/resources/assets/blockvault/patterns/default.json`; custom rules are appended to the built-ins, and invalid entries are skipped. The file is read when a server connection starts, so reconnect after editing it.

## Data and security

Vault data is stored outside the game directory: `%LOCALAPPDATA%/BlockVault` on Windows, `~/Library/Application Support/BlockVault` on macOS, and `$XDG_DATA_HOME/blockvault` or `~/.local/share/blockvault` on Linux. Set the Java property `blockvault.dataDir` to use another location.

During vault creation, choose whether to trust this device. When enabled, BlockVault stores the vault key locally so it can unlock without asking for the master password. Anyone with access to your computer account and BlockVault data can then unlock it. BlockVault has no cloud sync or inactivity auto-lock. Passwords sent to a server go only to the Minecraft server you are connected to.

Custom prompt detection is based on server messages and can be spoofed. Review generated or edited credentials before sending them. Copied passwords remain on the clipboard.

## Contributing

The project targets Java 25. Each loader and the pure-Java core has its own Gradle wrapper. Run `./gradlew build` in `fabric/` and `neoforge/`, and `./gradlew test` in `core/`.

- `core/`: vault storage, cryptography, password generation, detection, and session logic.
- `shared-client/`: Minecraft screens, settings, hooks, and mixins.
- `fabric/` and `neoforge/`: loader entry points and event integration.

Before treating behavior as verified, work through [the manual test checklist](TEST_CHECKLIST.md). Design and merge rules are in [DESIGN.md](DESIGN.md) and [CONFLICT_HANDLING.md](CONFLICT_HANDLING.md).
