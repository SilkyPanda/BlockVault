# BlockVault

BlockVault is a client-side password manager for Minecraft servers that use login plugins. It creates unique passwords per server and stores them in an encrypted local vault. It makes no network connections. The server-side component and cloud sync are out of scope.

## Builds and jars

GitHub Actions builds and tests each push and pull request. The build workflow uploads one jar for Fabric and one for NeoForge as run artifacts; download them from the successful Actions run. There are no automated releases or publishing steps. The owner releases manually.

There are independent Gradle builds in `core/`, `fabric/`, and `neoforge/`. CI uses Java 25 and Gradle 9.7.1 when a Gradle wrapper is unavailable. Local builds are best-effort; only a green Actions run verifies a commit.

## Manual in-game checks

Test on Paper with AuthMe (offline-mode or a cracked-friendly setup), then on a server using nLogin or LoginSecurity if available.

1. Fresh join -> register prompt -> BlockVault card shows a generated password -> confirm -> registered -> toast "Saved".
2. Leave and rejoin -> autofill sends `/login` once -> logged in.
3. Change the saved password in the vault manually -> rejoin -> wrong password message -> Rejected card appears, no retry loop.
4. Type `/login <right password>` manually -> Update card appears.
5. Check the game folder: typed login/register commands are NOT present in the command history file.
6. Kill the game right after `/register` -> relaunch -> vault shows “Pending — server not confirmed” (the credential is retained so you can retry or update it). If the server did accept it, manually log in once; a matching pending entry becomes verified so auto-login can work on the next join.
7. With the default `Trust this device` setting, unlock once with the master password, restart Minecraft, and confirm the vault opens without asking again. Then select `Ask for master password every launch`, restart, and confirm it asks again. Confirm inactivity auto-lock closes sensitive screens; with trust enabled, a later vault action in the same session may silently unlock again.
8. Join a vanilla server (no login plugin) -> nothing happens, no log spam.
9. Join singleplayer -> nothing happens.
10. Confirm a successful auto-login prints “Logged in automatically.” in green BlockVault chat text.
11. Both jars: Fabric and NeoForge behave identically.
12. Disconnect and watch `logs/latest.log`: no passwords, no secrets, no stack traces from BlockVault.

`Trust this device` is enabled by default, as an explicit product choice. It stores the 32-byte vault key in `vault.bv.trust`; it never stores the master password and does not use an operating-system keychain. This convenience weakens at-rest protection: anyone or any malware with access to the current OS account and data directory can open the vault without the master password. Select `Ask for master password every launch` to remove the trust file. POSIX permissions are restricted to the owner; Windows relies on the data directory's OS profile ACL and BlockVault does not independently verify or set a Windows ACL.

## Local data and cleanup

BlockVault stores data outside the Minecraft game folder:

- Windows: `%APPDATA%\BlockVault`
- macOS: `~/Library/Application Support/BlockVault`
- Linux: `$XDG_DATA_HOME/blockvault`, or `~/.local/share/blockvault` when `XDG_DATA_HOME` is unset

The directory can also be overridden with the Java system property `blockvault.dataDir`.

`vault.bv`, the automatic `vault.bv.bak1` through `vault.bv.bak3` files, and manual `vault.bv.manual1` through `vault.bv.manual10` files hold encrypted vault snapshots, including saved server passwords. With `Trust this device` enabled, `vault.bv.trust` holds the random vault key, not the master password. `config.json` stores preferences only. Optional `patterns.json` adds prompt and result patterns and command aliases to the built-in pattern pack; user regexes are limited to 256 characters.

The vault screen can export a byte-for-byte encrypted vault copy to a new file; export refuses to overwrite an existing file. Importing accepts that encrypted file and its master password, then merges server/account entries by host and modification time. Exports remain sensitive files: protect them as you would a password-protected vault and keep the master password separate.

Deleting an account creates a tombstone in the current vault; tombstones are purged after 30 days. Backups are encrypted snapshots of the entire vault, including saved accounts, rather than separate copies for each password. On ordinary vault saves, BlockVault creates at most one automatic snapshot per 24-hour period and keeps three; backup 1 is newest and 3 oldest. Empty vaults do not produce automatic snapshots, so the first useful save can be preserved. This is save-triggered, not a timer. You can also create manual snapshots in Settings; up to ten are kept separately from automatic backups. The backup screen shows each snapshot's type, slot, and creation time. Once unlocked, copying, revealing, editing, and inspecting backups do not ask for the master password again. Open one to inspect saved server/account names and selectively reveal passwords while the vault is unlocked. Restoring either type validates the snapshot, saves the current vault into the automatic backup set so restore can be undone, replaces the vault contents, and keeps the current master password. If the main vault is missing or damaged, choose `Recover from backup` on the access screen and enter the password used to encrypt the selected backup. Changing the master password re-encrypts all automatic and manual backups so the old password no longer unlocks them. Settings can open the BlockVault data directory or erase all its contents after displaying a warning and verifying the master password. This permanently deletes the vault, saved passwords, backups, trusted-device key, preferences, patterns, and exports in that directory. Close other Minecraft instances first. The game cannot safely remove its own launcher-managed mod file; uninstall BlockVault from the launcher separately. Filesystem deletion is not a guaranteed secure wipe of recoverable disk blocks.

Copying a password places it on the operating-system clipboard. BlockVault does not clear it automatically. Clipboard history features or clipboard manager apps may retain additional copies; clear those separately if needed.

## Login statuses

A saved account marked “Pending — server not confirmed” was written before BlockVault sent a registration command, but the server success response was not observed. This protects against losing the credential if Minecraft closes during registration. If registration succeeded, a successful manual login with that same password promotes it to verified; verified accounts are eligible for auto-login.


## Security limits

The encrypted vault protects data at rest when `Ask for master password every launch` is selected. After inactivity auto-lock, trusted mode can silently unlock again on the next vault action during that game session; the explicit Lock now action waits for the next password entry. Trusted-device mode keeps the vault key alongside the vault. Neither mode protects against malware running as your OS user, a compromised Minecraft process, screenshots or clipboard history, or a server that receives credentials. Java strings and UI text fields can retain temporary secret copies until garbage collection.

Prompt detection uses server system messages, which a server or plugin can spoof. BlockVault only sends a credential to the server to which Minecraft is currently connected; review unexpected prompts before accepting them.

Creating or changing a master password requires at least 8 characters; when changing it, the new password must differ from the current one. Use a long, unique master password and keep a separate backup of it. A forgotten master password cannot be recovered unless you still know the password that encrypted an available backup. The memorable password preset is easier to type but has lower entropy than the longer random presets.

Unlock reports an incorrect password separately from malformed or unauthenticated vault contents. If authentication fails on the password-wrapped vault key, the file format cannot distinguish a mistyped password from damage to that wrapped key; BlockVault reports an incorrect password in that case.
