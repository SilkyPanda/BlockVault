# BlockVault

BlockVault is a client-side password manager mod for Minecraft servers that use login plugins (AuthMe-style `/register` and `/login`). It keeps one unique, generated password per server in an encrypted local vault and fills it in for you when the server asks. It never connects to any external service.

Runs on **Minecraft 26.3** with **Fabric** or **NeoForge** (Java 25).

> ## ⚠️ Alpha: please read first
>
> **Do not use BlockVault as your daily driver yet. Keep your own copy of your passwords** (write them down or store them in a real password manager) so you can never be locked out of an account.
>
> BlockVault was coded mainly by Codex (OpenAI). I have tested the code and its features myself, but it is **not complete enough for a public release** and needs human contributors to test it and fix what is broken.
>
> **If you find a bug or have an improvement, please [open an issue](https://github.com/SilkyPanda/BlockVault/issues) or a pull request.** Reports from real servers are the most useful thing you can send.

## What it does

- **Registers for you.** When a server asks you to register, BlockVault generates a strong password, shows it on a card, and sends it only after you confirm.
- **Logs in for you.** Saved, verified logins are filled in automatically on later joins. Typing a bare `/login` also sends the saved password (this can be switched off).
- **Encrypted vault.** Passwords are stored in a local file encrypted with AES-256-GCM. The key comes from your master password (PBKDF2-HMAC-SHA512).
- **Safety nets.** Deleted accounts go to a 60-day trash, automatic backups are kept, and the vault can be exported and imported (always with a preview first).
- **Privacy options.** A streamer mode hides server and account names, and typed login commands can be kept out of your chat history.

## Install

You need Minecraft 26.3 and one of:

| Loader | Also needed | Optional |
|---|---|---|
| Fabric (Loader 0.19.5 or newer) | Fabric API | Mod Menu, YACL |
| NeoForge | none | YACL |

Put the BlockVault jar in your `mods` folder. Without YACL, BlockVault shows its own simpler settings screen. With YACL installed, it uses YACL's.

## Using it

1. Open the settings from Mod Menu (Fabric) or the mod list (NeoForge), then open the vault. The first time, you create a master password (8 to 256 characters) and choose whether to trust this device (see below).
2. Join a server with a login plugin. On a register prompt, review the generated password on the card and confirm it. On a login prompt, BlockVault fills in the saved password.
3. Manage saved accounts, per-account autofill and command format, the trash, backups, import/export and the master password from the vault and settings screens.

Things to know:

- **Prompt detection is English-only.** On servers that talk in another language, registration is not confirmed automatically and you may need to confirm the pending login yourself.
- You can add your own prompt, result or command-name rules in `patterns.json` in the data folder (copy the structure of [`default.json`](core/src/main/resources/assets/blockvault/patterns/default.json)). The file is read when you join a server.
- BlockVault only listens for login prompts for a short time after you join a server.

## Where your data lives

Everything is stored outside the game folder:

- Windows: `%LOCALAPPDATA%/BlockVault`
- macOS: `~/Library/Application Support/BlockVault`
- Linux: `$XDG_DATA_HOME/blockvault` or `~/.local/share/blockvault`

You can use another folder with the Java property `blockvault.dataDir`.

## Security notes

- **Trust this device.** When on (the default), BlockVault stores the vault key on this computer so it can unlock without asking for your master password. Anyone who can read your user account's files can then open the vault. Turn it off to be asked every launch.
- There is no cloud sync and no automatic lock after inactivity.
- Copied passwords stay on your clipboard until you replace them.
- A password is only ever sent to the Minecraft server you are connected to. Prompt detection reads server messages, which a server or another player's chat can imitate, so look at what a card shows before you confirm it.
- Memory wiping of secrets is best effort; Java does not guarantee it.

## Building

The project targets Java 25. The pure-Java `core` and each loader have their own Gradle wrapper:

```
cd core      && ./gradlew test
cd fabric    && ./gradlew build
cd neoforge  && ./gradlew build
```

- `core/`: vault storage, encryption, password generation, prompt detection, login logic (no Minecraft dependency, fully unit-tested)
- `shared-client/`: screens, settings, hooks and mixins
- `fabric/`, `neoforge/`: loader entry points

[ARCHITECTURE.md](ARCHITECTURE.md) explains how the pieces fit together and the rules the code follows. Pull requests are welcome. Please run the core tests, and try your change in the game on at least one loader.

## License

Copyright © 2026 SilkyPanda. BlockVault is free software under the **GNU General Public License v3.0 only**. See [LICENSE](LICENSE).
