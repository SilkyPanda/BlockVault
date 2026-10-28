# BlockVault code review

*Review of BlockVault-main.zip (core, shared-client, fabric, neoforge) · 28 Oct 2026*

## How to read this

I read every source file, the mixin and loader metadata, the build files, CI and the docs. I compiled the `core` module with JDK 21 in a scratch folder and ran small probes against it, so items marked **\[verified\]** were reproduced. Items marked **\[code\]** come from reading the code. Items marked **\[in-game\]** depend on Minecraft 26.3 behaviour I can't check: the screens, mixins and loader hooks were not compiled against Minecraft here, and your STATUS.md says they have never been run in game. I also couldn't run your Gradle/JUnit suite (Maven Central isn't reachable from my sandbox), so the claimed 91 passing tests are unconfirmed.

Severity: **Critical** loses or leaks data in normal use. **High** is a real security or correctness problem. **Medium** is wrong behaviour in plausible situations. **Low** is an edge case or polish.

## Fix these first

1. **C1** Changing the master password zeroes every password in memory, and the next save writes the zeros to disk.
2. **H1** Trust this device is on by default, so the vault key sits next to the vault and the master password does little. Default to asking.
3. **H2** Typed `/l`, `/log`, `/unreg`, `/cp` and namespaced commands are neither captured nor scrubbed from history.
4. **M1–M4** The login state machine has four real bugs: a nag loop, silent failure, a wrong registration template and wrong failure classification.
5. **M6** Erase all data fails for any custom data folder and doesn't delete everything its warning promises.
6. Dead legacy code (section 4) and secret-handling claims in DESIGN.md that the JSON layer doesn't honour.

## 1. Critical and high

### C1. Changing the master password wipes the live vault's passwords, and the next save makes it permanent \[verified\]

**Where:** `VaultStore.changeMasterPassword`, `commitRotation`, `VaultMerger.mergeServer`, `Account.clearPassword`.

**What happens:** `commitRotation` merges the in-memory vault with the file on disk, and `VaultMerger` reuses the same `Account` objects from the in-memory vault. After the commit, `changeMasterPassword` calls `clearVaultPasswords(vault)`, which zeroes those arrays, then installs the merged vault, which still points at the same accounts.

**Probe:** the password read `hunter2-secret` before the change and 14 NUL characters after it. I then saved an unrelated server (what the mod does on the next login or new account) and reopened the file with the new password: the first account's password was 14 NULs on disk and the other account was fine.

**In game:** autofill fails (the template renderer rejects control characters, so you get the send-failed toast), Reveal and Copy return NULs, and the first write after that (a new account, `markVerified`, a template change) saves the damage. `reload()` does not heal it because the file digest matches the one just written. The only good copy is the restore point taken at the start of the rotation, until later backups push it out.

**Why the tests miss it:** the change-password tests lock and reopen the store before reading passwords.

**Fix idea:** build the post-rotation vault from a JSON round trip (as `commitImport` already does) or deep-copy accounts in the merger; more generally stop wiping arrays that other vault instances may still reference (see S1). Add a test that reads `store.vault()` immediately after the change.

### H1. Trust this device is on by default \[code\]

`vault.bv.trust` holds the raw 32-byte vault key beside `vault.bv`, protected only by POSIX 0600 (nothing on Windows). Anyone or anything that can read the folder can open the vault, and with the default most users never type the master password after setup. DECISIONS.md records this as accepted, and a chat notice explains it, but the create screen doesn't offer the choice (the notice appears later, and only once you are on a server), and `/blockvault lock` is cosmetic while the file exists. In practice the master password only protects a copy of `vault.bv` that leaves the machine without the trust file.

**Fix idea:** default to asking, or make it an explicit choice on the create screen; keep a visible indicator in settings.

### H2. Typed-command capture and history scrubbing miss common aliases \[verified\]

Probe: only `login`, `register`, `reg`, `changepass`, `changepassword` and `unregister` are captured. `l hunter2`, `log hunter2`, `unreg pw`, `cp old new` and `authme:login hunter2` are not. AuthMe ships aliases like `/l`, `/log`, `/unreg` and `/cp` (check against the servers you target). `default.json` even has a prompt pattern for `/l` and `/log` but no matching command name, so the two lists disagree. `SensitiveCommands` strips a `plugin:` namespace but `LoginSession.COMMAND` does not allow `:`, so scrubbing and capture disagree too. Also not handled: `/email`, `/totp`, `/captcha`, `/recover` and admin forms such as `/authme register <player> <pw>`.

**Effect:** passwords typed with those aliases go to `command_history.txt` and no save card is offered.

**Fix idea:** one alias source (the pattern pack) used by capture, scrubbing and prompt matching; strip namespaces in both places; add the missing groups.

### H3. History scrubbing may not cover Minecraft's in-memory chat recall \[verified\]

The mixin cancels `CommandHistory.addCommand` only. The chat component keeps its own recent-message list for the Up arrow, which I can't see being touched. Separately, `Hooks.checkHistoryScrubbing` compares against one global timestamp, so a second sensitive command within 5 seconds can hide a failed hook.

### H4. Prompt detection fires on ordinary text \[verified\]

Probe: `[Ad] Join our discord. /register for rewards` and `<Steve> hey everyone use /register <password>` both classify as REGISTER. Any broadcast or relayed chat that mentions `/login` or `/register` before the first confirmed login can open a registration card or the master-password screen. On a server with no login plugin the state never reaches CONFIRMED, so it can keep happening (each kind can be dismissed once per connection). Result messages only act inside the 5-second window after BlockVault sends something, which limits spoofing, but a fake success flips a pending account to verified and a fake failure during autofill offers to forget the credential (recoverable from trash).

**Fix idea:** require the `<password>`-style placeholder, ignore messages that start with `<name>` or `[Tag]`, and keep prompt lists per language and plugin in the pattern pack.

## 2. Medium

### M1. Cancelling unlock on a combined login/register message loops \[verified\]

When one message mentions both commands, `LoginSession.onSystemMessage` looks the account up before `arm()` has moved the state to PROMPTED. If the owner cancels the unlock screen, `abandonPrompt` returns early because the state isn't PROMPTED, so nothing is dismissed. Probe: four repeats of the combined message caused four vault lookups (a plain login message caused one). With a locked vault that is an unlock screen on every repeat.

**Fix idea:** record the dismissal in the ambiguous branch too, and apply the same debounce.

### M2. A failed bare `/login` gives no feedback \[verified\]

`onResult` shows the rejected card only for AUTOFILL; PASSWORDLESS falls into the plain `state = REJECTED` branch. Probe: after a bare `/login` with a wrong stored password there was no card and no toast.

### M3. Registration template parsing swallows later commands \[verified\]

`REGISTER_COMMAND` captures everything to the end of the line as the argument list. Probe: `Use /register <password> <password> or /login <password>` produced `register {password} {password} {email}`, asking for an email nobody requested. The reverse order only works because it falls back to the default; on a no-confirmation server that default sends two arguments.

**Fix idea:** stop each command's arguments at the next `  /command ` token.

### M4. Rule rejections are classed as failures, and many rejections aren't recognised \[verified\]

`classify` checks FAILURE before PASSWORD\_RULE, so `Invalid password: must contain a digit`, `Invalid password format` and `Invalid password length, 8-16 characters` return FAILURE. That skips the regenerate-and-retry path and marks registration as dismissed; the 'invalid password' alternative in the rule patterns is unreachable. Probe results of NONE: `Your password is too weak`, `The chosen password isn't safe, please choose another one.`, `Password is unsafe`, `This password is too common`, `You are already logged in!`, `Login failed`, `Incorrect login credentials`. Unrecognised messages just time out silently after 5 seconds.

**Fix idea:** classify PASSWORD\_RULE first when a length or character hint is present, add weak/unsafe/common patterns, and add a corpus test per built-in pattern.

### M5. Settings sub-screens leave the settings screen and lose unsaved changes \[code\]

`dataCategory(parent)` hands the mod-list screen to Backups, Master password and Erase, so Back returns to the mod list, not the YACL screen. Import and Export pass the YACL screen correctly. Unsaved draft options are dropped either way.

### M6. Erase all data \[verified / code\]

- `DataDirs.delete` only deletes a folder named `blockvault` (any case). Probe: a folder named `mydata` is refused, so with `-Dblockvault.dataDir=` pointing anywhere else, Erase always fails.
- The erase screen says it deletes settings, patterns and exports, but the file filter only matches `vault.bv*` and `config.json*`. `patterns.json` and the `exports/` folder (encrypted vault copies) are left behind.
- It first needs a readable `vault.bv` to verify the password, so a damaged or missing vault can't be erased in-app while the trust file, backups and `.damaged` copies may still exist.

### M7. Passwords with spaces or nothing are accepted in several places \[code\]

Commands are space-separated, but the card password field, the Edit screen and the Add screen allow spaces, and the card's Register path doesn't reject empty input (only the Update-rejected path checks). A password with a space is stored while the server sees extra arguments. The generator avoids spaces and quotes, so only hand-typed passwords are affected.

**Fix idea:** one shared validator (no whitespace or control characters, sensible length) used by cards, Edit and Add.

### M8. Vault versioning contradicts the 'unknown fields are kept' promise \[code\]

`VaultJson.read` rejects any `v` other than 2 with `IllegalArgumentException`, so a vault written by a future build is reported as damaged, not unsupported. (The file container is version 1 and the JSON is version 2: two numbers with similar names.) Other parse failures (`JsonSyntaxException`, `NullPointerException` on a missing field) aren't mapped to `VaultCorruptException` in `unlock`, `restoreBackup`, `decryptExternal` or `unlockTrustedDevice`. Unknown-field preservation only helps within version 2.

### M9. Recovery reports failure after it succeeded \[code\]

In `recoverDamagedVault`, a failure writing the trust file is caught by the generic handler after the vault was already replaced and unlocked, so the screen says recovery failed and never opens the vault. `openWithMasterPassword` handles the same failure correctly. Also `vault.bv.damaged` is a single slot, so a second recovery overwrites the first damaged copy.

### M10. Server entries can't be deleted \[code\]

Deleting the last account leaves the server (name, host list, template, autofill) forever, and there is no UI to remove it. The vault keeps a permanent list of every server you've saved a password for, even after the trash is emptied; only Erase all removes it. Server names are the raw `host:port` string rather than the server-list name. Delete server entires if all the passwords are deleted from that server.

### M11. `modifiedAt` doubles as last-login, and every login rewrites the vault \[code\]

`markVerified` sets both `modifiedAt` and `lastUsedAt`, so the merge rule 'newer `modifiedAt` wins' favours whichever copy logged in last, not whichever has the newest password. Each login also re-encrypts, fsyncs and may rotate a backup.

**Fix idea:** keep `modifiedAt` for password changes and update only `lastUsedAt` on login (or batch it).

### M12. Backup slots are shared and unmanaged \[code\]

Daily backups and the restore points taken before import, restore and master-password change all rotate the same three `bak` slots, so a few imports in one day push out every daily backup with near-identical copies. Manual backups (ten) can't be named or deleted. Ages come from file modification time, which cloud sync or restoring from an archive can change.

### M13. Multi-instance merge edge cases \[code\]

`save()` merges with disk but always keeps the local server settings, so a stale instance overwrites template, autofill and host-list changes made elsewhere. A stale instance can also bring back an account another instance trashed (the documented trade-off), leaving it live and in the trash at once. `create()` doesn't re-check existence under the lock, so two instances creating a vault at the same moment can overwrite each other.

### M14. 'Delete forever and purge backups' is incomplete \[code\]

`purgeFromBackups` matches host + username + exact password, so a backup holding an older password for the same account survives. It skips backups under an older key, `.damaged` and exports. Auto-expired trash (60 days) is never purged from backups. Errors are swallowed per file, so the count can't tell 'unreadable' from 'failed to write'.

### M15. `blockvaultPrevious` is write-only \[code\]

`pendingFields` stores a verified account's old password in the account metadata when a new registration is saved as pending. Nothing ever reads it back (there is no restore action); it is only stripped on verification. It is a plaintext secret, held as a String, that the owner can't use.

### M16. Card behaviour to check in game \[in-game\]

- A card opens straight over gameplay. If it takes initial focus while the player is moving, held keys could type into the generated password. There is no short input grace period.
- On a window resize `init()` rebuilds the fields from the original password array, discarding edits, and starts another 250 ms timer chain each time.
- Clicking outside a Save/Update card closes it, and that card is the only chance to store a manually typed password.

### M17. Join timing \[in-game\]

Both loaders defer `Hooks.onJoin` with `Minecraft.execute`, so a login prompt already queued behind the join packet may arrive before the session exists and be dropped. AuthMe repeats its reminder so it usually recovers, but a one-shot prompt would be missed.

### M18. Windows replace-over-open-file \[code\]

`atomicWrite` falls back only when atomic move is unsupported. A sharing violation from a second instance, antivirus or a sync tool surfaces as a failed save with no retry.

### M19. Autofill default is fail-open until config loads \[code\]

`LoginSession` starts with `globalAutofill = true`; the real value arrives after an asynchronous config load queued behind other I/O (including a key derivation of a few hundred milliseconds). A prompt in that window can autofill even if the owner turned it off.

## 3. Low severity, crypto and secret handling

### S1. Secret handling doesn't match DESIGN.md \[code\]

DESIGN.md says secrets are `char[]` or `byte[]` and are wiped. In practice `VaultJson.read` builds a String of the whole decrypted vault and a Gson tree holding every password as a String; `write` does `new String(password)` per account and `root.toString()`. The card, Edit, Add and Reveal text fields, `CommandTemplate.render`, clipboard copy, the master-password screens (`getValue()`), the `Normalizer` path in `MasterPasswordKek` and the backup viewer's `revealedText` all create immutable Strings too. Wiping the array copies is therefore mostly cosmetic, and the root cause of C1 is that `Account` is an immutable record that exposes `clearPassword()`, so copies and merges share mutable state.

**Fix idea:** either drop the wiping claims and the wiping of shared objects, or wipe only objects with a single owner and test it.

### S2. Key derivation \[code, verified\]

PBKDF2-HMAC-SHA512 is fast on GPUs. With an 8-character minimum and no strength feedback, an offline attack on a copied `vault.bv` is cheap when the password is weak. `Kdf.calibrate` takes one cold-JIT sample at the floor count, so it nearly always returns the 210,000 floor (in my sandbox it returned the floor and the real derivation then took about 600 ms).

**Fix idea:** raise the minimum length or add a strength meter, warm up before calibrating, and consider a memory-hard KDF (Argon2 or scrypt) if a vetted dependency is acceptable.

Fine as is: AES-GCM with random 96-bit nonces, header bytes authenticated as AAD, bounded file sizes and atomic writes.

### S3. Clipboard and locking \[code\]

Ignore.

### S4. Queued secrets and imports \[code\]

`clearWaiting()` drops queued operations without running their cancel callbacks, so any `char[]` copies they captured aren't wiped. A previewed import's decrypted contents sit in memory until commit or discard.

### S5. Smaller correctness and polish items

- `VaultFile.encode` accepts a payload that produces a file just over `MAX_BYTES`, which `parse` then rejects \[verified: an 8,388,592-byte payload gave an 8,388,714-byte file\]. Unreachable in practice, but the two checks should agree.
- `purgeExpired` keeps trash entries with a future `deletedAt` forever; the merger clamps future times for accounts but not for trash.
- `HostKey` accepts `999.999.999.999`, rejects non-ASCII (IDN) hostnames \[verified\], and gives different keys to aliases and the IP of the same server. LAN worlds get a new key per ephemeral port.
- Usernames are case-sensitive, but most auth plugins treat them case-insensitively, so `Steve` and `steve` become two accounts.
- `McSessionEnv.toast` uses vanilla's `PERIODIC_NOTIFICATION` id while `VaultRuntime.toast` has its own, so BlockVault toasts can replace or be replaced by vanilla ones.
- The 5-second manual-login timeout can be shorter than a slow server's reply.
- `PatternPack.load` reads `patterns.json` with no size limit and only at first join, so edits need a restart.
- After a successful export the screen closes without showing where the file was written.
- The import preview shows nine lines and then 'and N more' with no way to see the rest.
- After a partial backup re-encryption failure the password has changed (the message says so) but the screen stays in its failed state.

## 4. Dead, legacy and redundant code

The product has never been released (version 0.1.0, no release workflow, 'older vault files are not supported'), so most compatibility code has nothing to be compatible with.

**Never called in production**

- `LoginSession.attempted()` and `PromptMatcher.matchPrompt` (tests only).
- `BlockVaultConfig.Settings.withAutofill`, `withDelay`, `withDuration`, `withPosition`, `withStreamer`, `withGenerator` and `withScrubHistory`. Only `withTrustDevice` is used.
- The `SavedCredential` constructors with `ignoredLegacySavePolicy` (only tests use them).

**Leftovers from removed features**

- `neverSave`: the `ServerEntry` component, the first argument of `withPolicy`, the forced `false` on read and write, and the legacy JSON key.
- Whole-server trash (`TrashEntry.wholeServer`, its UI branch, 'older whole-server entries remain restorable'): nothing creates them any more.
- `VaultBackupDetailScreen` still skips accounts flagged `deleted` in metadata, a tombstone idea that was removed.
- `blockvaultPrevious` (M15).

**Abstractions with one user**

- `KeyMode` has one constant; the header's key-mode byte and validation exist only to check it equals that constant.
- `Platform` exists only to log the loader name.
- `Vault.v` accepts any value from 1 up and the merger takes `Math.max` of two versions, but only version 2 can ever be read.
- `Hooks.sensitiveAliases`, `SensitiveCommands.NAMES` and the pattern pack all list the same commands; `NAMES` is a subset of the default set.

**Duplication and smells**

- The 28-byte header prefix is built in `VaultFile.putPrefix` and again in `VaultStore.prefix`, with `PREFIX_BYTES` defined in both. `VaultStore.masterKek` is a one-line wrapper that `decryptExternal` bypasses.
- `Hooks.onOutgoingCommand` calls `active.onOutgoingCommand` in two branches. `LoginSession.handlePrompt` and `arm` both check `dismissedPromptKind`. `autofillStaged` duplicates `pending`.
- `PasswordGenerator.Policy` has four constructors and ten components, and `WORD_COUNT` (list size 1024) is easy to confuse with `memorableWordCount`.
- `CommandTemplate.builtins()` rebuilds a map and four validated records on every call, including from the `SavedCredential` constructor. `render` uses `StringBuffer`.
- Fully-qualified names are used throughout files that already import the types.
- There are two toast helpers, and `BlockVaultConfig.load` bounces I/O thread to game thread and back to I/O thread for every vault operation.
- `Settings` has eight near-identical copy methods.
- `CredentialConfirmScreen` and `McSessionEnv` poll with scheduled timers where a screen `tick` would do.
- Static mutable state (`store`, `session`, `patterns` and the flags in `Hooks`) is shared across threads without `volatile` in several places.
- `MasterPasswordKek.clear()` also zeroes the salt and AAD, so a reused instance would silently derive wrong keys.

## 5. Improvements worth considering

**Security and safety**

- Add a strength meter and raise the minimum master-password length.
- Keep automatic backups separate from restore points, and allow naming and deleting manual backups.

**Detection**

- Shared, data-driven alias and pattern lists, with more languages and plugins (nLogin, LoginSecurity and similar), and a corpus test per built-in pattern.
- A custom per-server command template (today there are only five built-in choices).
- A debug toggle showing why a prompt was or wasn't recognised, without secrets.

**UX**

- Show the export location, offer a file picker or recent folders, use the server-list name, add an edit-server screen and make usernames case-insensitive.

## 6. Build, CI and docs

- `fabric/build.gradle` pulls Mod Menu through the opaque Modrinth coordinate `maven.modrinth:mOgUt4GM:kyy7dbrZ`. Use the named Mod Menu artifact and pin versions.
- Three independent Gradle builds with three wrapper jars compile the same sources via `srcDir` instead of consuming `core` as a module. One multi-project build would remove the duplication, and CI currently tests `core` in a different compile context from the one that ships.
- The Gradle wrapper has no distribution checksum (a known gap) and the GitHub Actions aren't pinned to commit SHAs.
- NeoForge `loader_version_range=[1,)` accepts any FML version. `blockvault.mixins.json` is `required: true` with `compatibilityLevel` JAVA\_21, while DESIGN.md says a Minecraft update must never crash the game.
- The 256 KiB jar budget shaped the code: only 1,024 words are bundled and local-variable debug tables are stripped. That costs debuggability for no user-visible gain.
- DESIGN.md says game-thread code never does file work, but `Hooks.onJoin` loads `patterns.json` on the game thread at first join.
- The word list begins `acid, acorn, acre, acts, afar`, which looks like EFF's short list. EFF's lists are CC BY, and the README asks for credit if shipped, but no credit appears in the README, LICENSE or jar. It also uses only the alphabetically first 1,024 words (none from 'sn' to 'z'); still uniform at 10 bits per word, just a quirk.
- The erase warning promises more than the code deletes (M6).
- There are no tests for `shared-client`, the change-password tests never inspect in-memory state, and `LoginSessionTest` builds `SavedCredential` through the legacy constructor.
- CHANGES.md and DECISIONS.md read like a chat transcript ('the owner accepted the trade-off'). Consider a normal changelog once there is a release.

## 7. What looks solid

Atomic temp-file writes with fsync, a cross-process file lock, header bytes covered by AES-GCM AAD, bounded file sizes, rejection sampling in the generator, a time-boxed regex matcher for hostile server text, generation tokens in the login state machine so stale callbacks can't act, persisting a pending credential before sending the register command, and credentials sent only to the connected server. The `core` module is well separated and testable, which is why most bugs above were easy to reproduce.

## 8. Suggested order

1. C1, with a regression test.
2. H1 default and H2 alias list (one source of truth).
3. M1–M4 (state machine and patterns), M6 and M7.
4. M8–M10 and M12–M15 (data-model decisions).
5. Run every \[in-game\] item on a real client before release.
