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
6. Kill the game right after `/register` -> relaunch -> vault shows the PENDING account (no lockout).
7. With the default `Trust this device` setting, unlock once with the master password, restart Minecraft, and confirm the vault opens without asking again. Then select `Ask for master password every launch`, restart, and confirm it asks again.
8. Join a vanilla server (no login plugin) -> nothing happens, no log spam.
9. Join singleplayer -> nothing happens.
10. Both jars: Fabric and NeoForge behave identically.
11. Disconnect and watch `logs/latest.log`: no passwords, no secrets, no stack traces from BlockVault.

`Trust this device` stores a 32-byte vault key file with owner-only permissions. It does not store the master password or use an operating-system keychain. Anyone with access to your OS account can access that trusted-device file; select `Ask for master password every launch` to remove it.

## Local data and cleanup

BlockVault stores data outside the Minecraft game folder:

- Windows: `%APPDATA%\BlockVault`
- macOS: `~/Library/Application Support/BlockVault`
- Linux: `$XDG_DATA_HOME/blockvault`, or `~/.local/share/blockvault` when `XDG_DATA_HOME` is unset

The directory can also be overridden with the Java system property `blockvault.dataDir`.

`vault.bv` and the rotating `vault.bv.bak1` through `vault.bv.bak3` files hold the encrypted vault, including saved server passwords and any TOTP secrets. With `Trust this device` enabled, `vault.bv.trust` holds the random vault key, not the master password. `config.json` stores preferences only. `patterns.json` is optional custom prompt-detection data.

Deleting an account in the vault removes it from the current vault; older encrypted backups rotate out as later saves occur. BlockVault does not automatically erase its data directory when you uninstall the mod. To remove everything, exit Minecraft and delete the BlockVault data directory above. This also removes all saved accounts, backups, the trusted-device key, and settings.

Copying a password to the clipboard schedules a clear after 30 seconds. If Minecraft exits before that timer runs, clear the operating-system clipboard yourself.
