# BlockVault review

How findings were checked:
- **[probe]** reproduced by running the real `core` classes in a small harness
- **[code]** read in the source, not executed
- **[in-game]** needs Minecraft to settle

`core` compiled on JDK 21 and its 113 tests all passed (about 22 s, down from about 48 s before KDF calibration was cached). `shared-client`, `fabric` and `neoforge` cannot be built here, so findings in them are **[code]** at best. `TEST_CHECKLIST.md` records your own in-game passes, and I relied on it for things like "both loaders launch".

---

## 1. Verdict

The blockers from review 3 are gone:
- Both loaders launch (you ticked this off), and the NeoForge metadata now has `modLoader` and `loaderVersion`.
- `/l` and `/log` are no longer swallowed on unrelated servers.
- The license is now GPL-3.0-only in all three places (`LICENSE`, `fabric.mod.json`, `neoforge/gradle.properties`).
- YACL is now optional, and the trust choice is explained on the create screen.

Before uploading, I would also do these small things:
1. Fix N1 below. Bare `/login` now reaches the server as well as being auto-filled.
2. Fix N2 below. It is a one-line classification issue that can offer to save a mistyped password.
3. Update `STATUS.md`. It still says no build, test or in-game check has ever run, which contradicts your checklist.

Listing hygiene for Modrinth:
- Use a version like `0.2.0-alpha.1` on the alpha channel. **Implemented in both loader and core version metadata; not published.**
- Say in the description: English-only prompt detection, "don't use passwords you care about yet", and where the data lives. **Implemented in README and loader descriptions.**
- List Fabric API as required and YACL and Mod Menu as optional. **README documents these dependencies.**
- Link the source, since it is GPL. **README links the repository and GPL-3.0-only license.**

---

## 2. What happened to the review 3 items

| Item | Status |
|---|---|
| B1 `/l` and `/log` swallowed | **Fixed** for short aliases (cancelled only after a server prompt named them). Caused a new regression, see N1. |
| B2 rejected password re-offered | **Fixed** [probe]. A rule rejection now deletes the pending entry. |
| B3 "already logged in" shown as rejection | **Fixed** [probe], but see N2. |
| B4 PENDING dead end | **Fixed** with a new `PENDING_LOGIN` card [probe]. Non-English success messages still never confirm (N6). |
| B5 NeoForge `modLoader` missing | **Fixed**. |
| B6 saving a setting disabled trusted unlock | **Fixed**. |
| B7 post-commit failure shown as failure | **Fixed**. Vault locks with a clear toast. |
| B8 NFC password length | **Fixed** [probe]. Count is taken after normalisation, and the "damaged" mapping for `IllegalArgumentException` is gone. |
| B9 `neverSave` and `whole` dropped silently | Still true [probe]. Harmless. |
| B10 Windows replace retry | **Fixed** (`FileSystemException`). |
| B11 reload patterns tears down session | **Gone** (the action was removed). |
| B12 history-scrub check ordering | Open [in-game]. Your checklist says commands stay out of history, but a false "hook inactive" toast is untested. |
| B13 backups under an old password | **Fixed** (backup password screen). |
| B14 server-only rows | **Fixed** (rows removed). |
| B15 preview and merge matching | **Fixed** (id first, then host). |
| B16 spoofable prompts | **Partly**. 20 s listening window added, but chat-formatted lines still pass (N5). |
| B17 cards vanish after 5 s | **Fixed**. All cards stay until dismissed. |
| B18 list popping up over gameplay | **Fixed** (that refresh path was removed). |
| Trust default, KDF time, YACL hard dependency, open-data-folder reflection | **Fixed or removed.** Trust still defaults ON but now has an on-screen explanation. KDF target is 350 ms and cached. |
| Strength meter, memorable preset, caps-lock hint, `KeyMode`, dead strings | **Still there.** See section 7. |

---

## 3. New bugs and regressions

### Medium

**N1. Bare `/login` is no longer cancelled, so the server receives it too. [code] — Implemented.**
`Hooks.onOutgoingCommand` now cancels a bare login only if `wasShortAliasPrompted(name)` is true. That set only ever holds aliases of 3 characters or fewer, so the full word `login` never qualifies. Result: the server gets your bare `/login` (usually a usage message) and then BlockVault sends the real `login <password>`. It worked in your test, so you may not have noticed the extra line. Some login plugins count bare attempts. Suggested fix: cancel when the typed word is the full login command and a verified credential will be sent.

**N2. A manual `/login` typed while already logged in offers to save the typed password. [probe] — Implemented.**
Moving "already logged in" into the success patterns fixed B3, but a manual `/login mistypedpass` followed by the server's "You're already logged in!" now counts as a successful login. The probe produced a SAVE card offering `mistypedpass`. Accepting it would overwrite a good saved password. "Already logged in" should be its own neutral result: end the attempt, do not verify or capture.

**N3. Every save now decrypts the vault and the newest backup before checking the backup age. [code] — Implemented.**
`rotateBackupsIfDue()` runs on every save. The new "skip empty vaults" and "skip identical backup" checks (`fileHasBackupData`, `sameBackupContents`) run before the cheap 24-hour age test. That is three AES-GCM decrypts plus a JSON parse per save. For a small vault it is milliseconds, but the age check should come first.

**N4. Native settings screen uses fixed pixel positions with no scrolling. [code] — Implemented.**
Rows sit at fixed y offsets and the Back button is at y = 270 to 290, hint text at 294. Vanilla's "Auto" GUI scale on a 1920x1080 screen gives a 480x270 GUI, so Back would be off-screen. You tested common sizes and it passed, so check this specifically at Auto scale on 1080p and on small windows. Escape still works either way.
Also, the delay slider calls `update()` on every drag step. That rewrites `config.json` each time and rebuilds all widgets, which can drop the drag after the first step. Save on release instead.

**N5. Spoofable prompts are only narrowed, not closed. [probe] — Implemented.**
The listening window is 30 seconds. Prompt detection now rejects `<Steve>`, `[Steve -> me]`, `[Rank] Steve »`, and `Steve:` chat-shaped prefixes while retaining ordinary server instructions such as `Use /register to claim rewards`. Added matcher coverage; not run yet.

### Low

**N6. Success and failure detection is English only. [probe] — Implemented in listing/README.**
A Spanish "Te has registrado correctamente!" is not recognised, so after 5 s the session ends in UNKNOWN and the account stays PENDING. The new `PENDING_LOGIN` card makes this recoverable, but every registration on a non-English server will need that extra click. At minimum, say "English servers only" in the listing.

**N7. The 20 s prompt window is hard-coded and only resets on join. [probe, in-game] — Implemented with a 30-second window.**
A prompt after 30 seconds is ignored. World-change reset is not included.

**N8. The card has no input grace period. [code] — Implemented (250 ms).**
`setInitialFocus(passwordBox)` is immediate and Enter accepts a registration. A stray Enter in the moment a card appears could confirm it. Ignore input for about 250 ms after appearing.

**N9. Card scaling shrinks widgets but not text. [code] — Implemented.**
`scaled()` reduces box and button sizes on small windows, but Minecraft's font stays the same size, so labels such as "Reveal password" can clip. Stack the buttons vertically at small sizes instead of shrinking.

**N10. Erase no longer needs the master password. [code] — Kept per your earlier choice.**
This follows your decision. It means anyone at an unlocked game can erase the vault after a typed or clicked confirmation. It destroys data and does not expose it, and with Trust this device on, the vault was already open to that person.

**N11. `AccountPreferences` stores per-account settings inside the "unknown fields" metadata bag. [code] — Deferred until beta as requested by the alpha release scope.**
The format changed without a version bump, and `ServerEntry.autofill` and `templateId` now exist only for migration. That is fine for an alpha, but make a real schema change (and version bump) before beta. It also means my review 3 suggestion to remove unknown-field preservation no longer applies. It is load-bearing now.

---

## 4. Improvements and tidy-ups

- `Hooks.onJoin` still reads `patterns.json` from disk on the game thread, against your own design rule. **Implemented:** pattern loading now runs in the background and buffers early join messages.
- `ResultMatcher.classify` still runs on every message (about 12.7 µs [probe]). It is only needed in the `SENT` state. **Implemented:** classification runs only while a result is expected.
- The mixin config still declares `JAVA_21`. Check it against what current Fabric expects. **Implemented:** updated to `JAVA_25`.
- `closeBlockVaultScreen` still matches screens by class name, including a string for the YACL screen. A marker interface would be safer. **Implemented:** replaced class-name matching with `BlockVaultManagedScreen`.
- `vaultReturnScreen` is a static that keeps a Screen reference alive. **Implemented:** clear it when the flow completes or the access screen is cancelled.
- `Hooks.onJoin` and `applySettings` repeat the same configure call; `Settings` has eight `withX` methods that each re-list all fields. **Partly implemented:** shared configuration is in one helper; record-copy methods remain.
- Dead strings (5): `blockvault.add.empty_password`, `blockvault.config.value.seconds`, `blockvault.data.erase`, `blockvault.trash.row.server`, `blockvault.vault.delete`. No missing keys. **Implemented:** removed all five unused strings.
- `PasswordGenerator.Policy.defaults()` and `max()` are no longer reachable from the UI. **Kept:** current core tests still use these policies; no UI controls expose them.
- `STATUS.md` is stale (see section 1). **Implemented:** updated to distinguish previous checks from changes still needing verification.
- Test speed: `VaultStoreTest` dominates the run time because each test runs real KDF work. A test-only low iteration count would cut it a lot. **Not implemented:** preserve production KDF behavior in tests.

---

## 5. Card redesign

Implementation status is recorded by item. Cards remain until the owner dismisses them; an explicit action may close its card, per your choice. No timer auto-closes cards.

**Short answer on libraries: don't add one.** Reasons:
- The card is one small screen. A UI framework would add a dependency that must track every Minecraft 26.x rendering change on both loaders.
- Libraries such as owo-lib or ModernUI are bigger than the problem. Check their 26.3 support on both loaders before even considering them. YACL is configuration only and cannot help here.
- What you need is about a hundred lines of your own helper code and a few texture files.

**Implemented:** nine-slice panel with shadow and border, per-card accent colors, status chip, clipped identity/title, keyboard hint, responsive stacked actions, shared card-theme colors, and a subtle accent-strip animation with an Animations toggle in YACL and native settings. The panel stays fixed to avoid screen transition flicker. No Minecraft player head or server icon is shown.

**Look**
1. **9-slice sprite panel:** Implemented with `assets/blockvault/textures/gui/sprites/credential_card.png` and nine-slice metadata.
2. **Identity:** Partly implemented; username and host are shown. No head or server icon.
3. **Status chip:** Implemented with both text and color.
4. **Button hierarchy and icons:** Partly implemented; labels remain text buttons, and actions retain vanilla button styling.
5. **Theme constants:** Partly implemented in `CredentialCardTheme` for color; dimensions and durations remain in the screen.
6. **Vanilla layout widgets:** Not implemented. Narrow cards stack controls and keep normal text size.
7. **Keyboard hints:** Implemented.

**Animation**
1. **Timing:** Implemented with elapsed nanoseconds.
2. **Enter/exit motion:** Partly implemented. The accent strip expands on entry and retracts on close (200 ms / 120 ms); the panel itself does not slide or fade. Password buffers are wiped as closing starts.
3. **Input during motion:** The screen remains fully rendered while the accent animates; input is ignored during the 200 ms entrance and the 250 ms N8 grace period.
4. **Result display:** Implemented progress, success, failure, timeout, and already-logged-in states. The card stays open until you use Close or Esc; submitting an action shows its result instead of dismissing the card.
5. **Micro-feedback:** Not implemented; Copy, Reveal, and Regenerate retain their current behavior.
6. **Sound:** Not implemented.
7. **Animations toggle:** Implemented and enabled by default. Reduced-motion preference integration is not implemented.
8. **Lighter alternatives to a modal screen:**
   - Passive messages ("Saved", "Logged in", "Autofill sent") should not open a screen at all. Use a vanilla-style toast, which slides in and out natively. **Already use toast/chat feedback for these passive results.**
   - Reserve the full card for cases where you need input (register, update, rejected, pending login). **Current card variants require input or confirmation.**
   - You already avoid dimming the background, which is good. **Retained.**

Two practical caveats: the 26.x rendering API is new ("extract" style), so confirm the exact pose and sprite calls against your mappings, and keep every animated value in fields on the screen so a resize (`init()` is called again) does not restart or lose it.

---

## 6. Core tests that can go

**Removed (9 tests, none protect real behaviour):**

| Test | Why |
|---|---|
| `KeyModeTest` | **Removed.** Tests an enum with one value. `KeyMode` was removed too. |
| `SavedCredentialTest` | **Removed.** Checks a null default and a `toString`. |
| `SecureBytesTest` | **Removed.** Checks `Arrays.fill` and a random length. |
| `MasterPasswordKekTest` | **Removed.** Wrap and unwrap is already exercised by `VaultStoreTest` create and unlock. |
| `AesGcmTest` (both tests) | **Removed.** The NIST vector tests the JDK; tamper detection remains covered by `VaultFileTest`. |
| `DataDirsTest.honorsOverride` | **Removed.** The destructive-operation delete test remains. |
| `PasswordGeneratorTest` word-list parser tests (2) | **Removed.** They covered the bundled development word-list format. |

**Kept per your choice (optional low-value tests):**
- `KdfTest`: it only pins a JDK vector. A golden vault fixture file that must still open would protect format compatibility far better.
- `PromptModelTest`: fold the `toString` masking check into `LoginSessionTest`.
- `VaultFileTest`: the bounded fuzz test duplicates the exhaustive single-byte mutation test. `encoderRejectsFilesThatParserWouldConsiderOversized` is niche.
- `HostKeyTest.stillRejectsNonAsciiInIpv6LiteralsAndBadInternationalNames`: very niche.

**Keep:** `LoginSessionTest`, `VaultStoreTest`, `PatternMatcherTest` (including the ReDoS time-budget test and the corpus test), `CommandTemplateTest`, `VaultMergerTest`, `VaultTrashTest`, `InputRulesTest`.

**Tests worth adding for this round:**
1. Bare `/login` is not forwarded when a verified saved credential exists (N1). **Test added; not run.**
2. A manual `/login` followed by "already logged in" does not offer a SAVE card (N2). **Test added; not run.**
3. A prompt after the listening window is ignored. **Test added for 30 seconds; not run.**

---

## 7. Over-engineering

Ordered by payoff:
1. **Caps Lock hint and `KeyboardHandlerMixin`. — Removed.** The hint guessed state from typing patterns and required a keyboard mixin.
2. **`KeyMode` and the legacy server-level `autofill` and `templateId` fields. — `KeyMode` removed; legacy fields remain for migration.**

---

## 8. Suggested order of work

1. Run the unticked "Login detection and results" checklist items on a real server. **Not done; left for your manual test pass.**
2. Fix N1 and N2, then re-run the bare `/login` and "already logged in" checklist items. **Fixes and unit tests added; verification not run.**
3. Update `STATUS.md` and version metadata to `0.2.0-alpha.1`. **Implemented locally; release not published.**
4. After feedback: N4 to N9, test cleanup, card redesign, and over-engineering. **Implemented in this review except the per-item card redesign work explicitly marked above; tests/build not run.**
