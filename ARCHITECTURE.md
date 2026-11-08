# BlockVault architecture

## Modules and dependencies

- **`core`** contains plain Java vault storage, encryption, validation, prompt matching, password generation, and the login state machine. It does not depend on Minecraft or a loader.
- **`shared-client`** adapts core to Minecraft: screens, preferences, `Hooks`, session environment, vault facade and services, and mixins. It depends on core and Minecraft APIs, not Fabric or NeoForge APIs.
- **`fabric`** and **`neoforge`** register loader events and initialize the shared client. They depend on their own loader API and shared-client; core and shared-client do not depend on either loader.

```text
Incoming:
Fabric / NeoForge system-message callback
    -> Hooks -> LoginSession -> VaultAccess
    -> VaultRuntime -> VaultUnlockCoordinator / VaultAccountService
    -> VaultStore -> VaultFileAccess -> disk
    <- IO result -> game-thread callback -> McSessionEnv / BlockVaultScreen

Outgoing:
typed command -> command/history mixin -> Hooks -> LoginSession
    -> VaultAccess lookup -> VaultRuntime / VaultAccountService -> VaultStore
    -> LoginSession -> Hooks.sendOwnCommand -> Minecraft connection
```

## Data flow

Loader callbacks pass eligible system messages to `Hooks`. `Hooks` loads bundled and user prompt rules on the IO executor, buffers early join messages, and forwards messages to the active `LoginSession` on the game thread. `CommandPolicy` holds the pure command-name decisions used by the hooks. `LoginSession` decides when a saved credential is needed and calls `VaultAccess`, which is implemented by `VaultRuntime`.

`VaultRuntime` is a thin facade. `VaultUnlockCoordinator` owns the IO-confined `VaultStore` and unlock/trust flow; `VaultAccountService` performs account and trash work; `VaultTransferService` performs import/export and backup operations; `VaultScreenNavigation` changes screens and carries deferred destinations. File and KDF operations stay on the IO executor. Results that touch UI or session state are posted to the game thread.

In core, `VaultStore` remains the public storage API. `VaultImportManager` owns import preview/commit and temporary decrypted import data; `VaultBackupRotation` owns automatic/manual backup rotation; `TrustedDeviceKeyFile` owns the trusted-key file lifecycle; `VaultFileAccess` owns bounded reads, atomic copies/writes, permissions, and related low-level file operations. The cross-process lock and the remaining vault/save/restore coordination stay in `VaultStore`.

The outgoing-command mixin passes typed commands to `Hooks`. `CommandPolicy` recognizes a bare command token and applies the prompted-short-alias rule; `LoginSession` makes the session decision. BlockVault-generated commands go through `Hooks.sendOwnCommand`, which marks the send as internal. The command-history mixin uses `SensitiveCommands` and `Hooks` to keep passwords out of history.

## Thread ownership and mutable state

- **Game thread:** loader event adapters, screens and widgets, `Hooks.SessionState`, `LoginSession`, `McSessionEnv`, and screen-local fields. Session callbacks and UI callbacks run here. `Hooks.SELF_SENT` and `HISTORY_SCRUBBED` are per-thread markers.
- **IO executor:** `VaultStore` and its mutable in-memory vault/key state, config reads/writes, KDF, encryption, import, backups, and file IO. `VaultImportManager`, `VaultBackupRotation`, `TrustedDeviceKeyFile`, and `VaultFileAccess` are called from this executor. `VaultStore` is thread-confined; core tests may call it from their test thread.
- **Coordination:** `VaultUnlockCoordinator.waiting` is a concurrent deque because work is queued and drained across callbacks. Its unlock flags are volatile because game-thread entry points and IO callbacks share them. `VaultScreenNavigation` holds volatile access/deferred-destination flags and a pending return `Screen`; screen changes themselves run on the game thread. `BlockVaultConfig.current` is an immutable settings record published through a volatile reference; `loaded` and disk updates belong to IO work. `BackgroundExecutor` owns its executor references under synchronization. `VaultSettingsScreenFactory` tracks generated optional-YACL screens weakly on the game thread.

Pattern loading is already performed by an IO task; early messages are queued until the pattern pack and settings are ready. Do not move file/KDF work onto the game thread or invoke screen/session callbacks directly from the IO executor. 
## Settings and persisted data

| Data | Location | Notes |
|---|---|---|
| Client preferences (autofill, passwordless login, delay, card position, streamer mode, generator, history scrubbing, trusted-device choice, card animations) | `<data dir>/config.json` | Non-secret. Read once at startup in the background; screens never wait for it. |
| Trusted-device key | `<data dir>/vault.bv.trust` | Local key material, separate from the vault and the settings. |
| Vault: servers, accounts (with their autofill and command-format settings), trash | `<data dir>/vault.bv` | Encrypted. Schema version 1. |
| Prompt patterns | `<data dir>/patterns.json` plus the bundled `default.json` | The user file extends the bundled rules. |
| Backups and exports | Under the data directory by default | Always encrypted. |

The checked-in `GoldenVaultTest` opens a vault file that was written once and committed. If it fails, the on-disk format changed and existing users would be locked out. Change the format on purpose, bump the version, and regenerate the fixture only deliberately.

## Project rules

**Security**
- No network access, external processes or native code. `scripts/check-rules.sh` checks the obvious cases in CI.
- Keep secrets in `char[]` or `byte[]` and wipe them when done. Strings, Gson values and text widgets can keep copies, so wiping is best effort and must never be described as a guarantee. Never log a password, key or vault content; log exception class names only.
- Anything read from a server (chat text, command trees) is untrusted: cap its length, match it under a time budget, and never let it delete data.
- Writes are atomic (temp file, fsync, rename) and files are owner-only where the platform allows it.
- Passwords accept Unicode letters, marks, numbers, punctuation and symbols, but not whitespace, control or invisible characters, or `§`. Server passwords are 1 to 100 characters, master passwords 8 to 256 (after NFC normalisation). A paste containing a forbidden character is rejected as a whole, never silently rewritten.

**Minecraft integration**
- Game-version-specific code lives in `shared-client`, loader APIs in `fabric` and `neoforge`, everything else in `core` so it can be unit-tested without the game.
- Keep mixins few and small, with `require = 0` so a Minecraft update cannot crash the game. Because that makes failures silent, the privacy-critical hooks check themselves at runtime and warn when inactive.
- No per-tick work. The game thread never does file or key-derivation work; use `BackgroundExecutor`.
- **Change screens only through `ScreenSwitcher.show(...)`.** Do not call `Minecraft.setScreenAndShow(...)`: since 26.2 it renders an extra frame right away, which shows up as a black flash. Do not build a screen empty and fill it in later (a "loading" screen that swaps itself out); have the data ready before the screen opens. Do not close a screen just to open another a moment later; open the next one directly.

**Dependencies**
- YACL is the only optional external dependency (the settings screen). Do not bundle it. Do not add other runtime dependencies without discussing it first.

**Out of scope:** server-side plugins, cloud sync, browser import, accounts or telemetry, an OS keychain.

**Check on every Minecraft update:** the mixin targets (`ClientPacketListener.sendCommand`, `CommandHistory.addCommand`, `ChatComponent.addRecentChat`), the screen and widget APIs used by the screens, and both loaders' event and config-screen APIs.

## Merging vaults

Several copies of a vault can exist: two game instances sharing a data folder, an export, a backup. `VaultMerger` reconciles them with one rule:

1. Two server entries are the same server if they share at least one host (`host:port`). Accounts are matched by username.
2. If both copies have the account, the newer `modifiedAt` wins; on a tie the local copy wins. A timestamp in the future counts as "now", so an imported file cannot win forever.
3. Anything present on only one side is kept. Merging never deletes. Deleting moves an account to the trash (60 days).
4. Edits, deletes, trash restores and backup restores do not merge: they apply to the freshly reloaded vault and are saved only if the file is unchanged since it was read, otherwise they fail with "Vault changed on disk".
5. Imports are previewed first, and a restore point is taken before they are applied.

Trade-off: because deletions are not remembered, merging an older copy can bring back an account you deleted.

## Where to change X

| Change | Start here |
|---|---|
| Add a prompt or result pattern | `core/src/main/resources/assets/blockvault/patterns/default.json`; matching is in core `PromptMatcher` and `ResultMatcher`. |
| Add or change a screen | `BlockVaultScreen` and the relevant screen class. Open it with `ScreenSwitcher.show(...)`. |
| Change the vault format | Core `VaultFile`, `VaultJson` and `VaultStore`, the core vault tests, and `GoldenVaultTest`. |
| Add a setting | `BlockVaultConfig.Settings` (read, write, defaults), the native settings screen, the YACL settings class, and the English strings. |
| Change a loader event | The matching `fabric` or `neoforge` adapter; keep shared behaviour in `Hooks` and `LoginSession`. |
| Change pure session or command decisions | Core `CommandPolicy`, `SessionPreferences` or `LoginSession`; keep Minecraft adaptation in `Hooks`. |
