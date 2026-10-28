# BlockVault

BlockVault is a client-side password manager for Minecraft servers that use login plugins (AuthMe, nLogin and similar). It creates a unique password per server, fills it in on later joins, and keeps everything in an encrypted local vault. It makes no network connections. Server-side components and cloud sync are out of scope.

## Using it

- Open the vault with `/blockvault` (both loaders), a keybind (unbound by default; Controls > Multiplayer), the NeoForge mod-list Config button, or Mod Menu on Fabric. `/blockvault lock` wipes the key from memory.
- On a register prompt a card shows a generated password; confirm to register. On a login prompt a verified account is filled in once per connection. Cards wait until no other screen is open. Registration cards remain until dismissed; the card-duration setting applies to idle save/update cards.
- The vault list lets you search, reveal, copy, edit, change a server's template or autofill policy, add a login by hand, import or export, and open Settings, backups and the trash. The Fabric Mod Menu and NeoForge mod-list settings entries use YACL, which must be installed alongside BlockVault.
- Deleting an account moves only that account to the **trash**. Server settings remain after its last account is deleted. Restore the account or delete it for good; anything left is removed after 60 days. Deleting for good can optionally also remove the password from backups. Older whole-server trash entries remain restorable.

## Statuses

A saved account marked "Pending - server not confirmed" was written before the registration command was sent, but the server's success message was not seen. If registration succeeded, a successful manual login with that password promotes it to verified. Only verified accounts autofill.

## Local data

Data lives outside the game folder:

- Windows: `%LOCALAPPDATA%\BlockVault`
- macOS: `~/Library/Application Support/BlockVault`
- Linux: `$XDG_DATA_HOME/blockvault`, or `~/.local/share/blockvault`

Override with the Java property `blockvault.dataDir`.

| File | Contents |
| --- | --- |
| `vault.bv` | Encrypted vault (accounts, servers, trash) |
| `vault.bv.bak1`-`bak3` | Automatic snapshots: at most one per 24 hours, newest is 1 |
| `vault.bv.manual1`-`manual10` | Manual snapshots (Settings) |
| `vault.bv.trust` | Raw vault key, only with `Trust this device` on (the default) |
| `vault.bv.damaged` | Copy of a vault replaced during recovery |
| `config.json` | Preferences only |
| `patterns.json` | Optional extra prompt/result patterns (regexes limited to 256 characters) |
| `exports/` | Default destination for encrypted exports when no folder is entered |

- **Export** writes a byte-for-byte encrypted copy named by BlockVault in the chosen folder, or in `exports/` by default. It refuses to overwrite a file. **Import** shows a preview of what would change, takes a restore point, then merges; see `CONFLICT_HANDLING.md`.
- **Backups** are snapshots of the whole vault. Restoring one first saves the current vault as a restore point and keeps your current master password. If the main vault is missing or damaged, choose `Recover from backup` on the unlock screen and enter the password that encrypted that backup.
- **Changing the master password** creates a new vault key and re-encrypts the vault and all backups. Copies made earlier (exports, cloud-synced files) keep the old key and old password.
- **Erase all data** (Settings) verifies the master password, then deletes BlockVault's own files. Other files in that folder are kept. It is not a secure wipe of disk blocks. Close other Minecraft instances first, and uninstall the mod from your launcher separately.

## Security notes

- `Trust this device` is on by default. It stores the vault key in `vault.bv.trust`, never the master password, and does not use an OS keychain. Anyone or any malware with access to your OS account and data folder can open the vault without the master password. Choose `Ask for master password every launch` to remove the file. POSIX permissions are owner-only; Windows relies on the profile folder's ACL and BlockVault does not set one.
- There is no inactivity auto-lock. `Lock now` wipes the key and waits for the next password entry.
- Neither mode protects against malware running as your user, a compromised Minecraft process, screenshots, clipboard managers, or a server that receives your credentials. Java strings and text fields can keep temporary copies until garbage collection.
- Copied passwords stay on the clipboard; BlockVault never clears it.
- Prompt detection reads server messages (including action-bar text), which a server or plugin can spoof. A credential is only sent to the server Minecraft is connected to. A failure-looking message never deletes a pending credential.
- The master password needs at least 8 characters. A forgotten master password cannot be recovered unless you know the password of an available backup. Wrong passwords are reported separately from damaged files, except where the format cannot tell them apart.
- The memorable preset is easier to type but has fewer bits than the random presets.

## Building and code map

CI (GitHub Actions, Java 25, Gradle 9.7.1) builds and tests every push and uploads one jar per loader; there are no automated releases. Each of `core/`, `fabric/` and `neoforge/` is an independent Gradle build with its own wrapper.

- `core`: crypto, vault model, storage, backups, prompt detection, password generation, host keys and the login state machine. Pure Java with unit tests.
- `shared-client`: the Minecraft-facing runtime, config, screens and the three mixins.
- `fabric`, `neoforge`: loader entry points and event bridges only.
- YACL is the one approved third-party runtime dependency, used only for the client settings screen.

Other documents: `DESIGN.md` (rules for contributors), `CONFLICT_HANDLING.md`, `TESTING.md`, `DECISIONS.md`, `STATUS.md`.

## Memorable word list

`core/src/main/resources/assets/blockvault/password-words.txt` feeds the memorable preset and contains the first 1,024 active words. To use EFF's short list, download https://www.eff.org/files/2016/09/08/eff_short_wordlist_1.txt and save it over that file; the loader accepts the dice-number format as downloaded, skips hyphenated entries and uses the first 1,024 words (10 bits each). EFF publishes its lists under a Creative Commons attribution license, so credit them here if you ship it.
