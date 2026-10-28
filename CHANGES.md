# Changes in this round

- **Removed** auto-lock and tombstones. **Added** the trash (60 days), manual add, import preview, `/blockvault` command, Caps Lock hint, backup purge on delete-forever, unknown-field preservation, Gradle wrappers.
- **Fixed** the stuck unlock screen, cancelled-unlock loops, server-rule parsing (and a built-in pattern that was silently dropped), deletion on failure messages, password-change and unregister detection, master-password key rotation, corrupt config handling, recovery safety, regenerate ignoring server rules, regex time limits, strict host and command validation.
- **Changed** Windows data folder to Local AppData and the default preset to server-friendly.

# Changes in the current work

- **Fixed** memorable password generation when server length rules are narrower than the preset's expected length but can still fit a sampled word combination.
- **Changed** vault deletion to trash one account at a time and leave server settings after the last account is removed. Older whole-server trash entries remain restorable.
- **Added** a second confirmation click for permanent trash deletion, optional export folder with generated filenames, and a default `exports/` folder under BlockVault's data directory.
- **Redesigned** settings with YACL categories and controls shared by Fabric and NeoForge. Updated NeoForge mod-list icon/banner metadata.
- **Removed** the edit-password Generate action and the vault screen's Config action; Back now closes the vault screen.
- **Kept** the jar under the documented 256 KiB limit by bundling only the 1,024 words the generator already consumes and retaining source/line debug data without local-variable tables.
- **Added** passwordless `/login`: typing a bare login command sends the verified saved password through the server's configured login template, regardless of global or per-server autofill settings.
- **Changed** credential capture to always be allowed, removed its per-server toggle and opt-out action, and kept autofill as a separate per-server setting.
- **Fixed** YACL Reset bindings to use BlockVault's actual default values instead of the values loaded when the screen opens. Restored the vault-row Copy label.
- **Changed** the default idle save/update card duration from 10 seconds to 5 seconds; the YACL setting remains configurable.
