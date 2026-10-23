# Decisions

## 2026-10-23 — Repair API dump and refresh deprecated Actions

The plan's `javap -public -protected` invocation fails on the installed JDK because those visibility selectors are mutually exclusive. Use `javap -protected`, which emits public and protected members, in both loader dump tasks. The supplied artifacts confirmed that every class dump had the same option error, so M3 API work remains blocked until CI regenerates both artifacts with this fix. The CI annotation report also showed Node.js 20 runtime and `setup-java@v4` deprecation warnings. Upgrade checkout to v7, setup-java to v6, setup-gradle to v6, and upload-artifact to v7; these upstream versions use Node.js 24. This changes build tooling only and leaves CI limited to build/test/artifact functions.

## 2026-10-23 — Include loader APIs in diagnostic class indexes

The refreshed dumps verify many Minecraft and loader APIs, but show that the class index contained Minecraft classes only, so missing Fabric and NeoForge class names could not be resolved as required by section 5.4. Index the relevant loader API jars for each build, dump nested callback types named by verified public signatures, and dump `CommandHistory` private members to inspect persistence. The class index confirms the 26.3 GUI class is `GuiGraphicsExtractor`, so replace the obsolete `GuiGraphics` target. M3 remains deferred until CI artifacts show the missing Fabric keybinding and NeoForge nested event signatures.

## 2026-10-23 — CI result reported by owner, but not attributable here

The owner reports that the repository built successfully. The supplied workspace has a read-only `.git` directory and no `gh` executable, so this agent cannot commit, push, or inspect Actions runs. At the time of the original report the API artifacts were not accessible; refreshed dumps are now available in the workspace and are documented below. M0 remains PARTIAL until its green run can be associated with a commit.

## 2026-10-23 — Gradle wrappers omitted

No repository history or wrapper-generation output is available in the supplied checkout. CI uses the specified Gradle 9.7.1 fallback. Wrapper generation is deferred until the real project checkout is available.

## 2026-10-23 — Gson vault password serialization uses a temporary immutable string

The specified Gson JSON tree API represents JSON string values as Java `String`. Vault serialization therefore creates a short-lived password `String` to construct the `pass` JSON value; the temporary `char[]` copy is wiped immediately. The JSON tree's UTF-8 byte buffer is wiped after encryption. Java cannot guarantee erasure of the immutable string before garbage collection.

## 2026-10-23 — Correct Fabric key mapping API from refreshed dumps

The updated Fabric class index contains `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`; the previous `client.keybinding.v1.KeyBindingHelper` dump reports class-not-found. Point the diagnostic list at the observed class and include NeoForge nested connection/screen events in the next dump so their callback signatures are available before M3 loader integration.

## 2026-10-23 — Local Gradle test attempt unavailable

`gradle --no-daemon test` failed before project configuration because the installed Gradle could not load `libnative-platform.so` for Linux amd64. Static rules and `git diff --check` pass. M2 remains PARTIAL until the updated tests compile and pass in GitHub Actions; this environment cannot provide that CI run.

## 2026-10-23 — Classify registration failure responses

The M2 CI run showed that `Registration failed!` did not match the default failure patterns, leaving a pending account undeleted. Add a narrow registration/account-creation failure expression and a matcher assertion so these responses enter the existing failure cleanup path.

## 2026-10-23 — Implement client hooks from refreshed 26.3 API evidence

The owner reports a green CI run at `f189cec`, so proceed with M3. Read the refreshed Fabric and NeoForge API dump artifacts from `/tmp/api-fabric` and `/tmp/api-neoforge` before implementation. Cross-check the cached 26.3 Minecraft jar where the public dumps omit bytecode: `CommandHistory.addCommand(String)` calls `save()` after appending and writes `command_history.txt`, so cancel at method HEAD prevents both memory and disk history entries. The `ClientPacketListener.sendCommand(String)` method handles command signing internally; hook this verified public method for manual capture. Use Fabric's dumped `KeyMappingHelper` spelling and `GLFW_KEY_UNKNOWN` for the plan's unbound default. This environment cannot compile: local Gradle exits before configuration because `libnative-platform.so` cannot load. Treat M3 as partial until CI passes.

## 2026-10-23 — Wipe captured-save staging array

`LoginSession.confirmCapturedSave` passes a temporary password clone into the asynchronous vault facade. Wipe that staging array immediately after the facade call; the shared client facade takes its own copy synchronously before queuing background work. Add a unit assertion for the handoff buffer. CI must validate this follow-up with M3.

## 2026-10-23 — Defer NeoForge development-environment detection

The supplied NeoForge API dump did not include the loader environment class, and Gradle cannot resolve/build the local NeoForge project in this environment. Avoid relying on an assumed API or a filesystem heuristic. `NeoForgePlatform.isDevEnvironment()` currently returns false with a verification TODO; no current feature reads the value. Add the specific loader class to the next diagnostic dump before wiring detection.

## 2026-10-23 — Dump CommandHistory bytecode before history interception

The refreshed API dump confirms `CommandHistory.addCommand(String)` and a private persistence method/path field, but the output does not include the constant value or method body. Extend the diagnostic dump to include private bytecode and constants for this one class so the M3 history hook can be checked against the actual persistence behavior before implementing it.

## 2026-10-23 — Classify registration failure responses

The M2 CI run showed that `Registration failed!` did not match the default failure patterns, leaving a pending account undeleted. Add a narrow registration/account-creation failure expression and a matcher assertion so these responses enter the existing failure cleanup path.
