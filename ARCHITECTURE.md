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

Pattern loading is already performed by an IO task; early messages are queued until the pattern pack and settings are ready. Do not move file/KDF work onto the game thread or invoke screen/session callbacks directly from the IO executor. A pre-existing damaged-vault recovery completion refreshes vault rows inline on the game thread; this refactor preserves that path.

## Settings and persisted data

| Data | Location | Notes |
|---|---|---|
| Client preferences (autofill, passwordless login, delay, card position, streamer mode, generator, history scrubbing, trusted-device choice, card animations) | `<BlockVault data directory>/config.json` | Non-secret settings. |
| Trusted-device key | `<BlockVault data directory>/vault.bv.trust` | Local key material separate from the encrypted vault and settings. |
| Vault, accounts, trash, per-account preferences | `<BlockVault data directory>/vault.bv` | Encrypted payload; account autofill/template preferences are account metadata. |
| Prompt patterns | `<BlockVault data directory>/patterns.json` plus `assets/blockvault/patterns/default.json` | The user file extends bundled rules. |
| Backups and exports | Under the data directory by default; exports may use a selected folder | Backup and export files remain encrypted. |

## Legacy and migration code

- `ServerEntry.autofill` and `ServerEntry.templateId` remain for legacy compatibility. `VaultJson` migrates them into per-account metadata when account preferences are absent.
- Account metadata preserves unknown fields for forward compatibility; `AccountPreferences` owns per-account autofill and command-template values.
- `VaultFile.Header.keyModeId` selection and validation remain part of the vault-file compatibility path.
- Malformed account records remain readable as unusable entries so their data is not silently discarded.

## Where to change X

| Change | Start here |
|---|---|
| Add a prompt/result pattern | `shared-client/src/main/resources/assets/blockvault/patterns/default.json`; matching logic is in core `PromptMatcher` and `ResultMatcher`. |
| Add or change a screen | `BlockVaultScreen` and the relevant screen class; use game-thread navigation and preserve its current layout. |
| Change vault format or account migration | Core `VaultFile`, `VaultJson`, and `VaultStore`, with the core vault tests. |
| Add a setting | `BlockVaultConfig.Settings`, config read/write/defaults, native and optional YACL controls, and English translations. |
| Change a loader event | The matching `fabric` or `neoforge` adapter; keep common behavior in `Hooks` and `LoginSession`. |
| Change pure session/command decisions | Core `CommandPolicy`, `SessionPreferences`, or `LoginSession`; keep Minecraft adaptation in `Hooks`. |
