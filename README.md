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
7. Switch to master-password mode -> restart -> join -> Unlock card appears only when a prompt is detected.
8. Join a vanilla server (no login plugin) -> nothing happens, no log spam.
9. Join singleplayer -> nothing happens.
10. Both jars: Fabric and NeoForge behave identically.
11. Disconnect and watch `logs/latest.log`: no passwords, no secrets, no stack traces from BlockVault.

Java 25 may emit a one-time warning when restricted Foreign Function & Memory APIs are used by the Windows device-key provider; this will be documented with that provider.
