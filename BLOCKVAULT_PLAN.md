# BlockVault - Implementation Spec (for Codex)

Repo: https://github.com/SilkyPanda/BlockVault.git (single `main` branch)
Target: Minecraft Java Edition 26.3, Fabric + NeoForge, client-side only.
Spec written: 2026-10-23. Version numbers below were checked against public sources on that date, but the ecosystem moves weekly: anything marked [VERIFY] must be confirmed against the real toolchain before you rely on it.

---------------------------------------------------------------------------
## 0. READ THIS FIRST - operating rules for the agent
---------------------------------------------------------------------------

1. Read this whole file before writing code. Then work through the milestones in section 6, in order.
2. **GitHub Actions is the source of truth for building.** Every push to any branch and every pull request must build everything on GitHub. Local builds are best-effort only (your sandbox may have no network and cannot always download Minecraft). Never claim something builds unless CI is green for that commit.
3. **Only implement build functions in CI. Do NOT implement releasing or publishing.** No GitHub Releases, no tag-triggered jobs, no Modrinth/CurseForge upload, no maven publishing, no secrets or tokens in workflows. CI builds, tests and uploads jar files as workflow artifacts. The owner releases manually.
4. Work in small commits directly on `main` and push after each logical commit (Conventional Commits: `feat:`, `fix:`, `build:`, `ci:`, `test:`, `docs:`, `refactor:`). After every push, follow the CI loop in section 5.3 until the run is green.
5. Do not stop to ask the owner questions unless you are blocked by missing credentials or permissions. Make a reasonable decision, and record it in `DECISIONS.md` (date, decision, reason).
6. **Never guess Minecraft/Fabric/NeoForge API names.** The game is unobfuscated in 26.x and names changed a lot in recent releases. Verify against real 26.3 jars or generated sources before using an API name. The M3 API dumps were a temporary diagnostic; they are not part of ongoing CI. If a later change needs an API that is not already verified, inspect the real toolchain first and record any toolchain decision. If it cannot be verified, isolate it in one small class, mark it `// TODO(verify-26.3)`, make any mixin `require = 0`, and keep CI green.
7. Keep `STATUS.md` current: for each milestone say DONE / PARTIAL / TODO, and list everything that compiled but was **not verified in-game** (the owner tests in-game manually).
8. Security and performance rules in section 2 are hard requirements. CI enforces some of them (`scripts/check-rules.sh`).

---------------------------------------------------------------------------
## 1. Project facts
---------------------------------------------------------------------------

| Item | Value |
|---|---|
| Name / mod id | BlockVault / `blockvault` |
| Base Java package | `io.github.silkypanda.blockvault` |
| Author | SilkyPanda |
| License | All rights reserved. Create `LICENSE` with: `Copyright (c) 2026 SilkyPanda. All rights reserved.` Fabric manifest `"license": "All-Rights-Reserved"`; NeoForge toml `license="All Rights Reserved"` |
| Mod version | `0.1.0` (jar version string `0.1.0+26.3`) |
| Minecraft | 26.3 (Java Edition, unobfuscated, Mojang class names) |
| Java | 25 (Temurin in CI) |
| Gradle | 9.x, minimum 9.4.1. CI fallback version 9.7.1 (see 5.1) |
| Fabric | Loader `0.19.5` or newer; Fabric API `0.161.0+26.3`; Loom plugin id `net.fabricmc.fabric-loom` (NOT `fabric-loom`, NOT `-remap`), version `1.18.2` (fall back to latest 1.17/1.18 that resolves) [VERIFY] |
| NeoForge | `26.3.0.7-beta` or the newest `26.3.0.*-beta` [VERIFY]; ModDevGradle plugin `net.neoforged.moddev` `2.0.148` (must be >= 2.0.147; 2.0.141 cannot set up 26.3) [VERIFY] |
| Not supported | classic Forge, Quilt, Architectury, Yarn/Parchment mappings, any other Minecraft version |

Resolve exact versions if the pinned ones fail:
- Fabric API: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml (pick newest ending `+26.3`)
- NeoForge: https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml (pick newest starting `26.3.`)
- Fabric Loader/Loom: https://maven.fabricmc.net/ metadata for `net/fabricmc/fabric-loader` and the Loom plugin marker.

What the mod is: a client-only password manager for servers that use login plugins (AuthMe, nLogin, LoginSecurity and similar). It detects `/login` and `/register` prompts, generates a unique password per server, stores it in an encrypted local vault, autofills it on later joins, and shows a Proton-Pass-style "Save login?" card. It makes **zero network connections**. No server-side component.

---------------------------------------------------------------------------
## 2. Non-negotiable requirements
---------------------------------------------------------------------------

### 2.1 Security
- Vault is always encrypted: AES-256-GCM (JDK `javax.crypto`), random 96-bit nonce per write, header authenticated as AAD.
- Never log, print, or put into exception messages any password, key, or decrypted vault content. Credential-like classes override `toString()` to return a masked value.
- Never use `System.out`, `System.err`, `printStackTrace()`. Use a logger.
- Use `char[]`/`byte[]` for secrets inside `core` and zero them with `Arrays.fill` when done (best effort; the JVM cannot guarantee wiping).
- Never overwrite a vault file that fails authentication. Keep it, surface an error.
- Vault lives OUTSIDE any Minecraft instance folder (section 7.4).
- Autofill rules: only react to **system chat messages** (never signed player chat), at most **one autofill attempt per connection**, never retry after a failure message, only send to the server address the entry was saved for.
- Keep login-type commands out of vanilla command history (verify the mechanism in 26.3, section 8.3).
- No networking APIs anywhere (CI enforced). No telemetry, no update checks.

### 2.2 Performance and size
- No per-tick work. Do not register tick events.
- Mod init does no file IO, no crypto, no reflection. Target < 5 ms.
- Vault data is decrypted when a login prompt is detected or a user opens/uses a vault feature. After unlock, compare the encrypted-file digest on each operation to detect another instance's atomic write; decrypt and parse only when that digest changes. Read the snapshot under the same file lock used for writes.
- All crypto and file IO run on one daemon background thread; results return to the game thread with `Minecraft.execute(...)`. Never run a KDF on the render thread.
- Chat path: cheap prefilter (message contains `/` or a known keyword) before any regex; stop matching once the session reaches CONFIRMED.
- UI classes load lazily (first time a dialog opens).
- Jar size budget: target < 200 KiB; CI fails above 256 KiB per loader jar.
- No third-party runtime libraries. No jar-in-jar. No shading. Use JDK crypto and Gson (already bundled with Minecraft; `compileOnly` in core).
- Max 3 mixin classes, `require = 0` on every injector.

### 2.3 Code style
- Java 25, 4-space indent, UTF-8, LF line endings, no tabs, no wildcard imports.
- Prefer `record`, `sealed`, `enum`, immutable data. Final classes by default.
- JSON: map by hand with Gson `JsonObject`/`JsonArray` tree API. No reflection-based Gson binding.
- Every public class in `core` has unit tests. Javadoc on public API explaining behavior and threading.
- All user-visible strings go through translation keys (`assets/blockvault/lang/en_us.json`).

---------------------------------------------------------------------------
## 3. Repository layout (create exactly this)
---------------------------------------------------------------------------

There are THREE independent Gradle builds (no root Gradle build). This avoids Loom and ModDevGradle plugins conflicting, lets CI run them as separate jobs, and keeps the two loader folders self-contained. Shared code is included into each loader build as extra source directories (no shared jar).

```
BlockVault/
|- .github/workflows/build.yml
|- .gitattributes
|- .gitignore
|- LICENSE
|- README.md
|- STATUS.md
|- DECISIONS.md
|- BLOCKVAULT_PLAN.md                 (this file)
|- scripts/check-rules.sh
|- core/                              standalone Gradle build, pure Java, unit-tested
|  |- settings.gradle
|  |- build.gradle
|  |- gradle.properties
|  `- src/{main,test}/java/io/github/silkypanda/blockvault/core/...
|     src/main/resources/assets/blockvault/patterns/default.json
|- shared-client/                     NO Gradle build; just sources compiled into BOTH loader jars
|  `- src/main/
|     |- java/io/github/silkypanda/blockvault/{BlockVault.java,Platform.java,...}
|     |- java/io/github/silkypanda/blockvault/{hooks,session,ui,mixin}/...
|     `- resources/{blockvault.mixins.json, assets/blockvault/{lang,textures,...}}
|- fabric/                            standalone Gradle build (Loom)
|  |- settings.gradle
|  |- build.gradle
|  |- gradle.properties
|  `- src/main/{java/.../fabric/BlockVaultFabric.java, resources/fabric.mod.json}
`- neoforge/                          standalone Gradle build (ModDevGradle)
   |- settings.gradle
   |- build.gradle
   |- gradle.properties
   `- src/main/{java/.../neoforge/BlockVaultNeoForge.java, resources/META-INF/neoforge.mods.toml}
```

Rules:
- `core/` must not import any `net.minecraft`, Fabric or NeoForge class.
- `shared-client/` may import `net.minecraft.*` and `core`, but NOT Fabric API or NeoForge classes. (Unobfuscated 26.x means the same Mojang names compile on both loaders.)
- Loader folders contain only entrypoint glue: event bridges, keybind registration, loader manifests, loader-specific `Platform` implementation.
- Each loader build adds `../core/src/main/{java,resources}` and `../shared-client/src/main/{java,resources}` to its main source set.
- Wrapper: commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` inside each of `core/`, `fabric/`, `neoforge/` IF you can generate them (`gradle wrapper --gradle-version 9.7.1`). If you cannot, do not block: CI has a no-wrapper fallback (5.1). Note it in `DECISIONS.md`.

---------------------------------------------------------------------------
## 4. Build files (starting point; adapt if the real toolchain demands it)
---------------------------------------------------------------------------

### 4.1 `.gitignore`
```
.gradle/
build/
out/
run/
.idea/
*.iml
.vscode/
.classpath
.project
.settings/
*.log
!gradle/wrapper/gradle-wrapper.jar
```

### 4.2 `.gitattributes`
```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
*.png binary
```

### 4.3 `core/settings.gradle`
```groovy
rootProject.name = 'blockvault-core'
```

### 4.4 `core/gradle.properties`
```
org.gradle.jvmargs=-Xmx1G
org.gradle.caching=true
```

### 4.5 `core/build.gradle`
```groovy
plugins {
    id 'java-library'
}

group = 'io.github.silkypanda.blockvault'
version = '0.1.0'

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType(JavaCompile).configureEach {
    options.release = 25
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-Xlint:all', '-Werror']
}

dependencies {
    // Gson ships with Minecraft at runtime, so it is compileOnly here. Never shade it.
    compileOnly 'com.google.code.gson:gson:2.13.2'

    testImplementation 'com.google.code.gson:gson:2.13.2'
    testImplementation platform('org.junit:junit-bom:5.13.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'skipped', 'failed'
        exceptionFormat 'full'
    }
}
```
If `-Werror` with `-Xlint:all` blocks progress on a harmless warning, narrow the lint set and record it in `DECISIONS.md`; do not remove `-Werror` entirely without a reason.

### 4.6 `fabric/settings.gradle`
```groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'blockvault-fabric'
```

### 4.7 `fabric/gradle.properties`
```
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.caching=true

minecraft_version=26.3
loader_version=0.19.5
fabric_api_version=0.161.0+26.3
loom_version=1.18.2

mod_id=blockvault
mod_version=0.1.0
maven_group=io.github.silkypanda.blockvault
```

### 4.8 `fabric/build.gradle`
```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

version = "${mod_version}+${minecraft_version}"
group = project.maven_group

base {
    archivesName = 'blockvault-fabric'
}

// Shared sources: pure-Java core + Minecraft-facing shared client code.
sourceSets {
    main {
        java {
            srcDir '../core/src/main/java'
            srcDir '../shared-client/src/main/java'
        }
        resources {
            srcDir '../core/src/main/resources'
            srcDir '../shared-client/src/main/resources'
        }
    }
}

loom {
    mods {
        "blockvault" {
            sourceSet sourceSets.main
        }
    }
}

dependencies {
    // Minecraft 26.x is unobfuscated: NO mappings line, plain `implementation` (not modImplementation).
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

processResources {
    def v = project.version
    inputs.property 'version', v
    filesMatching('fabric.mod.json') {
        expand 'version': v
    }
}

tasks.withType(JavaCompile).configureEach {
    options.release = 25
    options.encoding = 'UTF-8'
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

jar {
    from('../LICENSE') {
        rename { "${it}_blockvault" }
    }
}


```

### 4.9 `fabric/src/main/resources/fabric.mod.json`
```json
{
  "schemaVersion": 1,
  "id": "blockvault",
  "version": "${version}",
  "name": "BlockVault",
  "description": "Client-side password manager for servers with login/register plugins. Generates, encrypts and autofills per-server passwords.",
  "authors": ["SilkyPanda"],
  "contact": {
    "sources": "https://github.com/SilkyPanda/BlockVault"
  },
  "license": "All-Rights-Reserved",
  "environment": "client",
  "entrypoints": {
    "client": ["io.github.silkypanda.blockvault.fabric.BlockVaultFabric"]
  },
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "~26.3",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```
Add `"mixins": ["blockvault.mixins.json"]` in milestone M3 when mixins exist.

### 4.10 `neoforge/settings.gradle`
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = 'NeoForged'
            url = 'https://maven.neoforged.net/releases'
        }
    }
}

rootProject.name = 'blockvault-neoforge'
```

### 4.11 `neoforge/gradle.properties`
```
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.caching=true

moddev_version=2.0.148

minecraft_version=26.3
minecraft_version_range=[26.3,26.4)
neo_version=26.3.0.7-beta
neo_version_range=[26.3.0.7-beta,)
# Permissive on purpose; tighten using the MDK for 26.x if desired.
loader_version_range=[1,)

mod_id=blockvault
mod_name=BlockVault
mod_license=All Rights Reserved
mod_version=0.1.0
mod_authors=SilkyPanda
mod_description=Client-side password manager for servers with login/register plugins. Generates, encrypts and autofills per-server passwords.
maven_group=io.github.silkypanda.blockvault
```

### 4.12 `neoforge/build.gradle`
```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version "${moddev_version}"
}

version = "${mod_version}+${minecraft_version}"
group = project.maven_group

base {
    archivesName = 'blockvault-neoforge'
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

sourceSets {
    main {
        java {
            srcDir '../core/src/main/java'
            srcDir '../shared-client/src/main/java'
        }
        resources {
            srcDir '../core/src/main/resources'
            srcDir '../shared-client/src/main/resources'
        }
    }
}

neoForge {
    version = project.neo_version

    runs {
        client {
            client()
        }
    }

    mods {
        "blockvault" {
            sourceSet sourceSets.main
        }
    }
}

tasks.withType(JavaCompile).configureEach {
    options.release = 25
    options.encoding = 'UTF-8'
}

var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
        minecraft_version      : minecraft_version,
        minecraft_version_range: minecraft_version_range,
        neo_version            : neo_version,
        neo_version_range      : neo_version_range,
        loader_version_range   : loader_version_range,
        mod_id                 : mod_id,
        mod_name               : mod_name,
        mod_license            : mod_license,
        mod_version            : mod_version,
        mod_authors            : mod_authors,
        mod_description        : mod_description
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into layout.buildDirectory.dir('generated/sources/modMetadata')
}
sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

jar {
    from('../LICENSE') {
        rename { "${it}_blockvault" }
    }
}


```
This follows the standard NeoForge MDK pattern: the manifest template lives at `neoforge/src/main/templates/META-INF/neoforge.mods.toml` (NOT in `src/main/resources`), and Gradle expands `${...}` properties into it. If the real 26.x MDK differs (task names, DSL), follow the MDK and note it in `DECISIONS.md`. Also consider NeoForge's `neoForge.runs.client` `systemProperty` entries only if needed.

### 4.13 `neoforge/src/main/templates/META-INF/neoforge.mods.toml`
```toml
modLoader = "javafml"
loaderVersion = "${loader_version_range}"
license = "${mod_license}"

[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "${mod_name}"
authors = "${mod_authors}"
description = '''${mod_description}'''

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "${neo_version_range}"
ordering = "NONE"
side = "CLIENT"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "${minecraft_version_range}"
ordering = "NONE"
side = "CLIENT"
```
Add a `[[mixins]]` table with `config = "blockvault.mixins.json"` in milestone M3. If NeoForge clients are reported "incompatible" on vanilla servers during manual testing, research the current client-only marker for 26.x and apply it.

### 4.14 Entrypoints (M0, minimal)
Fabric: `io.github.silkypanda.blockvault.fabric.BlockVaultFabric implements ClientModInitializer`, calls `BlockVault.init(new FabricPlatform())`.
NeoForge: `@Mod(value = "blockvault", dist = Dist.CLIENT) public final class BlockVaultNeoForge { public BlockVaultNeoForge(IEventBus modBus, ModContainer container) { BlockVault.init(new NeoForgePlatform()); } }` [VERIFY the annotation and constructor signature in 26.3].

`BlockVault.init(Platform)` in M0 only stores the platform and logs one INFO line: `BlockVault initialized (<loader>)`.

---------------------------------------------------------------------------
## 5. GitHub Actions
---------------------------------------------------------------------------

### 5.1 `.github/workflows/build.yml` (create exactly; this is the only workflow)
```yaml
name: build

on:
  push:
    branches: ['**']
  pull_request:
  workflow_dispatch:

concurrency:
  group: build-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

env:
  JAVA_VERSION: '25'
  FALLBACK_GRADLE_VERSION: '9.7.1'

jobs:
  rules:
    name: Static rules
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v4
      - name: Check project rules
        run: bash scripts/check-rules.sh

  core:
    name: Core unit tests
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: core
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
      - name: Pick Gradle
        id: gradle
        run: |
          if [ -f gradlew ]; then echo "version=wrapper" >> "$GITHUB_OUTPUT"; else echo "version=${FALLBACK_GRADLE_VERSION}" >> "$GITHUB_OUTPUT"; fi
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: ${{ steps.gradle.outputs.version }}
      - name: Test
        run: |
          if [ -f gradlew ]; then chmod +x gradlew; ./gradlew --no-daemon test; else gradle --no-daemon test; fi
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: core-test-reports
          path: core/build/reports/tests
          if-no-files-found: ignore
          retention-days: 14

  loader:
    name: Build ${{ matrix.loader }}
    runs-on: ubuntu-latest
    timeout-minutes: 40
    strategy:
      fail-fast: false
      matrix:
        loader: [fabric, neoforge]
    defaults:
      run:
        working-directory: ${{ matrix.loader }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
      - name: Pick Gradle
        id: gradle
        run: |
          if [ -f gradlew ]; then echo "version=wrapper" >> "$GITHUB_OUTPUT"; else echo "version=${FALLBACK_GRADLE_VERSION}" >> "$GITHUB_OUTPUT"; fi
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: ${{ steps.gradle.outputs.version }}
      - name: Build
        run: |
          if [ -f gradlew ]; then chmod +x gradlew; ./gradlew --no-daemon build; else gradle --no-daemon build; fi
      - name: Jar size budget
        run: |
          shopt -s nullglob
          found=0
          for f in build/libs/*.jar; do
            case "$f" in *-sources.jar) continue;; esac
            found=1
            size=$(stat -c %s "$f")
            echo "${f}: ${size} bytes" | tee -a "$GITHUB_STEP_SUMMARY"
            if [ "$size" -gt 262144 ]; then echo "::error::${f} exceeds the 256 KiB budget"; exit 1; fi
          done
          if [ "$found" -eq 0 ]; then echo "::error::no jar produced"; exit 1; fi
      - name: Upload jars
        uses: actions/upload-artifact@v4
        with:
          name: blockvault-${{ matrix.loader }}-${{ github.sha }}
          path: |
            ${{ matrix.loader }}/build/libs/*.jar
            !${{ matrix.loader }}/build/libs/*-sources.jar
          if-no-files-found: error
          retention-days: 30
```
Requirements for this workflow:
- Triggers on every push (all branches), every PR, and manual dispatch.
- No `release`, `tags`, `secrets.*`, publish or deploy steps. Ever. If a newer major version of an action exists (checkout, setup-java, setup-gradle, upload-artifact), you may bump to it; keep `permissions: contents: read`.
- The jar artifacts are what the owner downloads and releases manually.
- If the pinned `FALLBACK_GRADLE_VERSION` fails to download, use the newest Gradle 9.x that exists (>= 9.4.1) and record it in `DECISIONS.md`.

### 5.2 `scripts/check-rules.sh` (create; must be executable; CI runs it)
```bash
#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
fail=0

# scan <pattern> <message> <exclude-regex-on-grep-output> <dirs...>
scan() {
  local pattern="$1" msg="$2" exclude="$3"
  shift 3
  local hits
  hits=$(grep -rEn --include='*.java' "$pattern" "$@" 2>/dev/null | grep -Ev "$exclude" || true)
  if [ -n "$hits" ]; then
    echo "$hits"
    echo "::error::$msg"
    fail=1
  fi
}

DIRS="core/src/main shared-client/src/main fabric/src neoforge/src"

scan 'import java\.net\.|import javax\.net\.|HttpClient|HttpURLConnection|URLConnection|java\.net\.Socket' \
  'Networking APIs are forbidden (the mod must make no network connections)' '^$' $DIRS

scan 'System\.(out|err)\.|printStackTrace\(' \
  'Use the logger; never System.out/err/printStackTrace' '^$' $DIRS

# External processes are forbidden in mod source.
scan 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec' \
  'External process execution is forbidden' '^$' $DIRS

# Mixins must declare require = 0
if grep -rEn --include='*.java' '@(Inject|Redirect|ModifyArg|ModifyVariable|ModifyExpressionValue|WrapOperation)\(' shared-client/src 2>/dev/null | grep -v 'require *= *0' >/dev/null; then
  echo '::warning::Check that every mixin injector declares require = 0 (multi-line annotations may be false positives)'
fi

# Required files
for f in LICENSE README.md STATUS.md DECISIONS.md .github/workflows/build.yml; do
  [ -f "$f" ] || { echo "::error::missing $f"; fail=1; }
done

# The workflow must never contain release/publish steps
if grep -Eiq 'softprops/action-gh-release|publish|modrinth|curseforge|secrets\.' .github/workflows/build.yml; then
  echo '::error::Release/publish/secrets are forbidden in the workflow (manual releases only)'
  fail=1
fi

exit $fail
```

### 5.3 CI feedback loop (follow after EVERY push)
1. After `git push`, find the run: `gh run list --branch main --limit 1`, then `gh run watch <id> --exit-status`.
2. If it fails: `gh run view <id> --log-failed`, read the first real error (not the cascade), fix it, commit, push. Repeat.
3. If `gh` is unavailable or unauthenticated: stop after the push, write the pushed commit hash and "CI not observed" in `STATUS.md`, and tell the owner to paste the failing log from the Actions tab.
4. Never mark a milestone DONE while its last commit has no green run.
5. A broken-on-purpose or red `main` must not persist: if you cannot fix within a reasonable effort, revert the offending commit and note it in `DECISIONS.md`.

---------------------------------------------------------------------------
## 6. Milestones (do in order; each ends with green CI on its last commit)
---------------------------------------------------------------------------

### M0 - Skeleton and CI (FIRST DELIVERABLE)
Tasks:
- Create every file in sections 3-5: `.gitignore`, `.gitattributes`, `LICENSE`, `README.md` (what it is, how CI builds, where to get jars, the manual-release note, and the in-game test checklist from section 12), `STATUS.md`, `DECISIONS.md`, `scripts/check-rules.sh`, workflow, three Gradle builds, manifests, entrypoints.
- `core`: class `core/BlockVaultInfo` with `static final String MOD_ID = "blockvault"` and a trivial JUnit test, so the test job has something to run.
- `shared-client`: `BlockVault` (static `init(Platform)`, logger via SLF4J `LoggerFactory`, which Minecraft provides) and `Platform` interface (section 8.1).
- Fabric and NeoForge entrypoints call `BlockVault.init(...)`.
- Generate Gradle wrappers if possible.
Acceptance:
- CI jobs `rules`, `core`, `loader (fabric)`, `loader (neoforge)` are all green.
- The Actions run contains artifacts `blockvault-fabric-<sha>`, `blockvault-neoforge-<sha>` (each a single jar `blockvault-<loader>-0.1.0+26.3.jar`, under 256 KiB), plus the core test reports. Do not keep API dump jobs in CI; if a future change needs a new unverified API, inspect the real toolchain for that change and remove any temporary diagnostic task afterward.
- No release/publish/secrets in the workflow.

### M1 - Core: crypto and vault storage (section 7.1-7.4)
Acceptance: all unit tests green in CI; vault round-trips, rejects tampering, handles corruption without data loss.

### M2 - Core: logic (section 7.5-7.8)
Generator, host normalization, pattern pack matcher, command templates, `LoginSession` state machine with a fake scheduler. Acceptance: all unit tests green; coverage of every state transition.

### M3 - Client wiring (section 8.1-8.4)
Verify every Minecraft, Fabric, and NeoForge API against the refreshed 26.3 dumps before implementing hooks, mixins, background executor, session listener, autofill sending `/login` and `/register`, history scrubbing, and keybinds. No fancy UI yet: use vanilla toasts and a minimal confirm screen. Acceptance: builds on both loaders; `STATUS.md` lists exactly which hooks were verified against dumps and which are unverified in-game.

### M4 - UI (section 8.5-8.7)
Save/Update/Register/Rejected card, vault screen, settings screen, translations. Acceptance: builds on both loaders; UI code uses only vanilla widgets.

### M5 - Master password and trusted-device convenience (section 7.3)
The vault has exactly one key mode: a master password. Do not add DPAPI, Java Foreign Function & Memory calls, macOS Keychain/`security`, Linux Secret Service/`secret-tool`, OS commands, or device key providers. Add a settings choice between `Ask for master password every launch` and `Trust this device`; `Trust this device` is the default. After successful new-vault creation, explain in chat that the trusted-device key is stored locally and this behavior can be changed in settings. In trusted mode, after the user has entered the master password successfully, store only the random 32-byte vault key in a separate local file under the BlockVault data directory. Do not store the master password. The vault retains its master-password wrapping format, while trusted mode deliberately adds a local unwrapping path. Trusting this device means granting anyone who can read the current OS account files access to the vault; it is not a second key mode or hardware-backed protection. Untrusted mode removes the local key file and requires the master password on every process launch. A bad/missing trust file falls back to the master-password screen without changing the vault. Trust file writes are atomic and limited to 0600 on POSIX. Windows relies on the OS profile-directory ACL; BlockVault does not create or verify a dedicated ACL there. Temporary key buffers are wiped when no longer needed. Creating or changing a master password requires at least 8 characters; changing also rejects reusing the current password, verifies the old password, rewraps the same vault key, and re-encrypts every automatic and manual backup with the new wrapper. Acceptance: core tests cover trusted unlock, missing/corrupt trust-file fallback, disabling trust, and password change; settings default, chat notice, password change, and both launch choices are checked in-game. `check-rules.sh` remains green.

### M6 - Polish and hardening
Import/export, streamer mode, auto-lock, docs, final performance review (section 2.2), jar-size review, `STATUS.md` final pass. Before M6 is DONE, resolve the confirmed critical/high findings in `BlockVault code review.md` or record an explicit accepted limitation in `DECISIONS.md`; preserve reproduced-vs-unverified labels, and do not mark loader behavior verified without in-game checks. Add regression tests for confirmed core findings and require green CI after each logical commit. Version stays `0.1.0`; the owner releases manually.

---------------------------------------------------------------------------
## 7. `core` specification (package `io.github.silkypanda.blockvault.core`)
---------------------------------------------------------------------------

### 7.1 `crypto`
- `AesGcm`: `static byte[] encrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext)` and `static byte[] decrypt(...)` throwing `VaultAuthException` (checked or unchecked, your choice; document it) on tag failure. 128-bit tag. Key must be exactly 32 bytes.
- `Kdf`: `static byte[] pbkdf2HmacSha512(char[] password, byte[] salt, int iterations, int dkLenBytes)` using `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")`. `static int calibrate(long targetMillis, int floor, int cap)` benchmarks once and returns an iteration count (default target 250 ms, floor 210_000, cap 2_000_000). Calibration runs only on the background thread, only when creating or changing a master password.
- `SecureBytes`: tiny helper with `static void wipe(byte[])`, `static void wipe(char[])`, `static byte[] random(int n)` (shared `SecureRandom`).
- `KeyMode` has only `MASTER_PASSWORD(2)`; device and OS-provider modes are forbidden.

### 7.2 `vault` data model (immutable records + builders)
```
Vault { int v; List<ServerEntry> servers; }
ServerEntry { String id (UUID); String name; List<String> hosts (normalized host:port);
              boolean neverSave; boolean autofill; String templateId; List<Account> accounts; }
Account { String username; char[] password; Status status (VERIFIED|PENDING);
          long createdAt; long modifiedAt; long lastUsedAt; }
```
`Account.toString()` never reveals the password. JSON field names are short and stable: `v`, `servers`, `id`, `name`, `hosts`, `neverSave`, `autofill`, `template`, `accounts`, `user`, `pass`, `status`, `created`, `modified`, `used`, plus active account metadata `deleted` and `blockvaultPrevious`. Unknown fields are discarded; missing required fields and unknown status values are errors. This intentionally does not provide forward compatibility with later vault schemas.

### 7.3 Key management
- Key hierarchy: a random 256-bit vault key (VK) encrypts the vault JSON. `MasterPasswordKek` wraps VK with PBKDF2-HMAC-SHA512(masterPassword, salt, iterations, 32) and AES-GCM using the header prefix as AAD. Changing the master password re-wraps VK without changing the current vault payload, and re-encrypts every backup using the new wrapper so old passwords no longer unlock backups.
- The default `Trust this device` convenience stores a separate copy of VK after a successful master-password unlock. It stores no password and does not create another vault key mode. The file is `vault.bv.trust` in the BlockVault data directory; POSIX mode is 0600. Windows relies on the profile-directory ACL, which BlockVault does not independently set or verify. Anyone with access to the current OS account and this file can unlock the vault, so the setting is explicitly a local trust decision, not a hardware-backed protection.
- `Ask for master password every launch` deletes `vault.bv.trust` and requires password entry after every process start. If the trust key is missing, malformed, or fails authentication, fall back to the master-password screen and leave `vault.bv` untouched.
- Do not use OS keystores, OS commands, `java.lang.foreign`, telemetry, or network access for trust mode.

### 7.4 `vault` file and storage
**Location** (`DataDirs`): Windows `%APPDATA%\BlockVault`; macOS `~/Library/Application Support/BlockVault`; Linux `$XDG_DATA_HOME/blockvault` else `~/.local/share/blockvault`. Overridable by a system property `blockvault.dataDir` (used by tests). Files: encrypted `vault.bv`, three automatic backups `vault.bv.bak1..3`, up to ten manual backups `vault.bv.manual1..10`, optional plaintext 32-byte vault key `vault.bv.trust` when trust-device mode is enabled, settings-only `config.json`, and optional user patterns in `patterns.json`. Interrupted saves may leave `vault.bv.tmp`; the lock file is `vault.bv.lock`. No automatic full-directory cleanup. On POSIX set owner-only permissions (0600 files, 0700 dir); on Windows rely on the per-user AppData ACL.

**Binary format v1** (all integers big-endian):
```
off  size  field
0    4     magic "BVLT"
4    1     formatVersion = 1
5    1     keyMode (2 MASTER_PASSWORD; all other values unsupported)
6    1     kdfId (1 PBKDF2-HMAC-SHA512)
7    1     reserved providerId (must be 0)
8    4     kdfIterations (0 if kdfId = 0)
12   16    kdfSalt
28   2     wrapLen
30   wrapLen  wrapped vault key (AES-GCM: nonce12 || ciphertext32 || tag16)
..   12    dataNonce
..   4     dataLen (ciphertext length including the 16-byte tag)
..   dataLen   ciphertext = AES-256-GCM(VK, dataNonce, aad = every header byte before the ciphertext, plaintext = UTF-8 JSON)
```
Wrap AAD (for AES-GCM wrapping) = header bytes [0, 28).

**`VaultFile`** (pure format code): `static byte[] encode(Header, wrapped, byte[] vk, byte[] jsonUtf8)` and `static Parsed parse(byte[] file)`. Strict bounds checks on every length field (reject `wrapLen` or `dataLen` that exceed the file size or 8 MiB); reject PBKDF2 iteration counts outside 210,000–2,000,000 before deriving a key. Unknown `formatVersion` -> `UnsupportedVaultVersionException`.

**`VaultStore`** (thread-confined: call only from the background executor):
- `static VaultStore open(Path dir)` (no key needed; reads header only), `boolean exists()`, `KeyMode keyMode()`.
- `create(char[] masterPassword)`, `unlock(char[] masterPassword)`, trusted-device enable/disable and trusted unlock methods, `Vault vault()`, `save(Vault)`, `lock()`, `changeMasterPassword(char[] old, char[] new)`. There is no mode-switch API.
- Saving: write to `vault.bv.tmp`, `FileChannel.force(true)`, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. Rotate three automatic encrypted backups at most once per 24 hours (`.bak1` newest ... `.bak3`); skip rotation while the current vault has no accounts; repeated saves within the interval must not replace the recovery point. Players can create up to ten manual encrypted snapshots (`.manual1` newest ... `.manual10` oldest), rotating only that set. The backup screen lists type and local creation time and decrypts a selected snapshot for account/password inspection after unlock. A restore validates and decrypts a selected backup first, snapshots the current vault into the automatic set, then atomically replaces current vault contents while retaining the current password wrapper. If the main vault is missing or damaged, a separate recovery path lists backups without unlocking the main file, validates the selected backup with the password that encrypted it, and restores that encrypted file as the main vault. Backups are encrypted full-vault snapshots; timestamps use file modification times.
- Locking: take an exclusive `FileChannel.lock()` on `vault.bv.lock` to capture a consistent encrypted-file snapshot for reads and to protect writes. Reload compares the encrypted file digest with the last snapshot and skips decrypting/parsing unchanged content; changed snapshots are decoded after the locked read completes. Explicit local edits compare the current encrypted file digest with the digest captured at reload and fail on a mismatch without writing. Merge rule for imports: per server `id`, per account `username`, keep the entry with the greater `modifiedAt`; keep the union of servers/accounts. Explicit local edits (including account rollback and server/account deletion) write the requested state only if the encrypted file digest still matches the last reload; otherwise fail with a conflict and leave disk untouched. Account deletions use tombstones (add `deleted: true, modified: <ts>` in JSON and purge after 30 days).
- If authentication fails on unlock: throw, leave the file untouched, never create a new empty vault over it.

**Tests (M1, all in `core/src/test`)**:
- AES-GCM known-answer vectors; tamper any single byte of header, nonce, ciphertext -> auth failure.
- PBKDF2 known-answer vector (RFC-style test vectors for HMAC-SHA512).
- Vault encode/parse round trip; truncated files; every length field out of range; wrong magic; future version.
- `VaultStore` create/unlock/save/lock cycles in a temp dir; distinguish wrong master password from damaged ciphertext; automatic backup rotation at the interval boundary; manual backup creation/rotation and inspection; restoring either backup type replaces contents and remains unlockable with the current master password; master-password change preserves the vault key and rejects an incorrect current password; atomic save leaves old file intact if the write is interrupted (simulate by failing the move); merge behavior with two stores on the same dir.
- JSON round trip preserves all fields defined by the current vault schema. Unknown fields are discarded; missing required fields and unknown enum values are errors.
- Fuzz test: 5,000 random mutations of a valid file must either parse or throw a controlled exception (never hang, never `OutOfMemoryError`, never loop).

### 7.5 `gen` - `PasswordGenerator`
- `String`-free API: `char[] generate(Policy p)`.
- `Policy` record: `int length` (8-64, default 20), `boolean upper, lower, digits, symbols`, `String symbolSet` (default `!@#$%^&*-_`), `boolean excludeAmbiguous` (`O0oIl1|` removed, default false).
- Preset factories: `defaults()`, `pickyServer()` (alphanumeric, length 16), `max()` (length 32, all classes), `memorable()` (normally 4 words from an embedded 1024-word list + 2 digits, joined by `-`; adjust the word count to fit a server min/max length hint while retaining the two-digit suffix; report incompatible constraints instead of silently changing preset; ship the word list as a resource in `core`).
- Algorithm: `SecureRandom`; rejection sampling for uniform index selection (no modulo bias); guarantee at least one character from each enabled class, then shuffle with Fisher-Yates using the same `SecureRandom`.
- Hard exclusions always: space, quotes (`'`, `"`), backslash, and any character outside printable ASCII 0x21-0x7E.
- - `Policy adjustFor(ServerRuleHint hint)`: given a parsed hint (`minLength`, `maxLength`, `alphanumericOnly`), return a stricter policy.
- Tests: length, class guarantees, forbidden characters never appear over 100k generations, chi-square uniformity sanity check over the character set, rejection sampling unit test with a fake random source.

### 7.6 `net`-free host handling - `HostKey`
`static String normalize(String address)`: lowercase, trim, strip a trailing dot, strip IPv6 brackets handling correctly, default port 25565 -> keep canonical form `host:25565` always. Examples: `Play.Example.NET` -> `play.example.net:25565`; `play.example.net:25570` -> same with 25570; `[::1]:25565` -> `[::1]:25565`. Tests for all of these plus malformed input (returns empty Optional, never throws).

### 7.7 `detect`
**Pattern pack** (`assets/blockvault/patterns/default.json`, in `core` resources; optional user file `<dataDir>/patterns.json` is merged on top):
```json
{
  "version": 1,
  "commandNames": { "login": ["login"], "register": ["register", "reg"] },
  "prompt": {
    "login":    ["(?iu)/login\\b"],
    "register": ["(?iu)/(?:register|reg)\\b"]
  },
  "result": {
    "success": [
      "(?iu)(successful(?:ly)?\\s+(?:log(?:ged)?\\s*in|login|registered)|logged\\s+in\\s+successfully|you(?:'| a)re\\s+now\\s+logged\\s+in|login\\s+successful|registration\\s+successful|successfully\\s+registered)",
      "(?iu)(sesi[o\u00f3]n\\s+iniciada|inicio\\s+de\\s+sesi[o\u00f3]n\\s+exitoso|registrado\\s+con\\s+[e\u00e9]xito|login\\s+realizado|registrado\\s+com\\s+sucesso)",
      "(?iu)(\u0443\u0441\u043f\u0435\u0448\u043d\u0430\u044f\\s+(?:\u0430\u0432\u0442\u043e\u0440\u0438\u0437\u0430\u0446\u0438\u044f|\u0440\u0435\u0433\u0438\u0441\u0442\u0440\u0430\u0446\u0438\u044f)|\u0432\u044b\\s+\u0443\u0441\u043f\u0435\u0448\u043d\u043e\\s+\u0432\u043e\u0448\u043b\u0438)"
    ],
    "failure": [
      "(?iu)(wrong|incorrect|invalid)\\s+password|password\\s+(?:is\\s+)?(?:wrong|incorrect)",
      "(?iu)(contrase\u00f1a\\s+incorrecta|senha\\s+incorreta|mot\\s+de\\s+passe\\s+incorrect|falsches\\s+passwort|\u043d\u0435\u0432\u0435\u0440\u043d\u044b\u0439\\s+\u043f\u0430\u0440\u043e\u043b\u044c)"
    ],
    "passwordRules": [
      "(?iu)(too\\s+short|too\\s+long|illegal\\s+characters|invalid\\s+characters|unsafe\\s+password|not\\s+allowed|at\\s+least\\s+\\d+|maximum\\s+of\\s+\\d+)"
    ]
  }
}
```
These are starting points. Real server messages vary; keep patterns tolerant and test them against a corpus (below). The only shipped locale is English; do not add identical English placeholder locale files. The loader must tolerate malformed structures and invalid regexes in a user-supplied pack (skip invalid entries, report a warning), cap each user-supplied regex at 256 characters, and retain built-in patterns when the user file is malformed.

- `PatternPack.load(InputStream defaults, Path userFileOrNull)`; `PromptMatcher`: `Optional<PromptKind> matchPrompt(String plain)` with a **prefilter**: if the string contains neither `/` nor any of a small keyword set, return empty without running regex. Also `ResultMatcher.classify(String plain)` -> `SUCCESS | FAILURE | PASSWORD_RULE | NONE` and `Optional<ServerRuleHint> parseRuleHint(String)` (extract numbers after "at least"/"maximum").
- Plain text only: the caller passes `Component.getString()`; strip any remaining `\u00a7x` color codes defensively.
- Test corpus: put at least 40 realistic messages in `core/src/test/resources/prompt-corpus.txt`, format `KIND<TAB>message`, covering AuthMe-style English defaults (e.g. "Please, login with the command: /login <password>", "Please, register to the server with the command: /register <password> <ConfirmPassword>", "Successful login!", "Wrong password!", "Successfully registered!"), usage lines, and unrelated chat ("Steve: type /login if you forgot", which must classify as NONE only because it is player chat - but note the matcher itself never sees player chat; keep one test showing the matcher is pure text and the caller is responsible for the system-message filter).

### 7.8 `session` - `LoginSession` and templates
Pure Java, no Minecraft imports. Inputs are plain method calls; outputs go through two interfaces so everything is unit-testable with fakes:
```java
public interface SessionEnv {            // implemented by shared-client
    void sendCommand(String commandWithoutSlash);
    void showSavePrompt(PromptModel m);   // Save / Update / Register / Rejected variants
    void toast(String translationKey);
    VaultAccess vault();                  // async facade over VaultStore (callbacks)
}
public interface Scheduler { void schedule(long delayMillis, Runnable r); long nowMillis(); }
```
State per connection: `IDLE -> PROMPTED -> SENT -> CONFIRMED | REJECTED | UNKNOWN`, plus a flag `attempted` (max one autofill attempt).

Inputs: `onJoin(String hostKey, String mcUsername)`, `onSystemMessage(String plain)`, `onOutgoingCommand(String commandWithoutSlash)` (for manual capture), `onDisconnect()`.

Behavior (this is the heart of the mod - implement and test every line):
1. **Arm**: a system message must match a login/register prompt before the session acts; do not inspect command-tree roots. Debounce repeated prompts: ignore further prompts within 1500 ms of the last one.
2. **Returning player** (vault has a VERIFIED account for hostKey + username, autofill on, not neverSave): wait `autofillDelayMillis` (default 400; configurable minimum 200), call `env.sendCommand("login " + password)` through the template for that server, set `attempted = true`, state SENT, start a 5000 ms result timer. Success pattern -> CONFIRMED (update `lastUsedAt`) and show a localized “Logged in automatically” chat message. Failure pattern -> REJECTED: show the Rejected card (Enter manually / Update / Forget). Timer expiry -> UNKNOWN (no action, no retry).
3. **New player** (register prompt, no entry): show the Register card with a generated password. On confirm: FIRST write the account to the vault with status PENDING, THEN send the register command, then wait for the result. SUCCESS -> mark VERIFIED, toast "Saved". FAILURE or password-rule complaint -> if rule complaint and not retried yet, regenerate once with `adjustFor(hint)` and offer the card again; otherwise delete the PENDING account. This ordering guarantees the player is never locked out by a crash right after a successful register.
4. **Manual capture**: `onOutgoingCommand` recognizes `login <pw>`, `register|reg <pw> [<pw>]`, `changepassword|changepass <old> <new>`, `unregister <pw>` (case-insensitive command names; aliases configurable via `commandNames`). Stash the password as pending IN MEMORY ONLY, wait for the result message, and on SUCCESS show Save (no entry), Update (entry exists with different password), or nothing (identical). If the matching entry is PENDING, promote it to VERIFIED instead of showing another save prompt. Do not write manual captures to disk before success.
5. `onDisconnect` resets state, wipes in-memory pending secrets, cancels timers.
6. Never autofill when `attempted` is already true. Never autofill without an actual matching system-message prompt.
7. Templates: `CommandTemplate` with placeholders `{password}`, `{email}`, `{username}`. Built-ins: `authme` (`login {password}`, `register {password} {password}`), `authme-noconfirm` (`register {password}`), `nlogin`, `loginsecurity` (same shapes as `authme`). A prompt containing exactly one `<...>` placeholder after `/register` selects `authme-noconfirm`; two selects `authme`; a third or an `email` word selects a template with `{email}` and asks the user for it in the Register card.
   Existing v1 server entries with `templateId: "authme"` are interpreted as automatic prompt detection for compatibility. A user who explicitly selects AuthMe stores `authme-explicit`; `auto` means automatic prompt detection.
8. Fake `Scheduler` in tests; cover: happy path autofill, wrong password, timeout, repeated prompt debounce, one-attempt rule, register success, register failure, rule complaint retry, crash between pending write and result (simulate by constructing a new session with a PENDING account present: it must show Update instead of generating a new password blindly), manual login capture then success, manual capture then failure (nothing saved), disconnect mid-wait, and cancel suppression lasting through disconnect.

---------------------------------------------------------------------------
## 8. `shared-client` specification
---------------------------------------------------------------------------

### 8.1 `Platform` and bootstrap
```java
public interface Platform {
    String loaderName();              // "fabric" | "neoforge"
}
```
`BlockVault.init(Platform)`: report the loader name, create (not start) the background executor lazily, register nothing heavy.
`BackgroundExecutor`: single daemon thread named `blockvault-io`; `submit(Callable)`; helper `onGameThread(Runnable)` -> `Minecraft.getInstance().execute(r)`. Created on first use.

### 8.2 Hooks (the entire Minecraft-facing surface). Verify each against the already-read M3 API dumps or real generated 26.3 sources before use
- Incoming **system** chat (not signed player chat): Fabric `ClientReceiveMessageEvents.GAME`; NeoForge `ClientChatReceivedEvent` (system variant). Loader glue calls `Hooks.onSystemMessage(Component)`.
- Join/disconnect: Fabric `ClientPlayConnectionEvents.JOIN/DISCONNECT`; NeoForge `ClientPlayerNetworkEvent.LoggingIn/LoggingOut`. Glue calls `Hooks.onJoin()` / `Hooks.onDisconnect()`. Server address and icon come from `Minecraft.getInstance().getCurrentServer()` (singleplayer/LAN: `null` -> do nothing at all).
- Minecraft username: `Minecraft.getInstance().getUser().getName()`.
- Do not inspect the server command tree. Arm only from a matching system prompt; command-tree presence does not prove the server is ready for authentication.
- Outgoing commands: ONE mixin class on the client packet listener's command-sending methods (both the signed and unsigned variants; names vary by version - verify), calling `Hooks.onOutgoingCommand(String)`. Commands sent by the mod itself are flagged with a ThreadLocal/static "self-sent" marker and ignored by the hook.
- Command history: mixin (or event if one exists) that stops commands whose first word is in the sensitive list (`login`, `register`, `reg`, `changepassword`, `changepass`, `unregister`, plus configured aliases) from being added to vanilla's command history and from the persisted history file. VERIFY the mechanism (class `CommandHistory` and the file it writes).
- Keybinds: shared `Keybinds` class builds the `KeyMapping`s (open vault screen, lock vault); each loader registers them with its own API (Fabric `KeyBindingHelper`; NeoForge `RegisterKeyMappingsEvent` on the mod bus). KeyMapping construction changed in recent versions (category type) - verify.
- Multiplayer-screen "Vault" button: loader glue adds a small button on `JoinMultiplayerScreen` via Fabric `ScreenEvents.AFTER_INIT` / NeoForge `ScreenEvent.Init.Post`. Optional; skip if it risks the build, note in STATUS.

Mixin config `shared-client/src/main/resources/blockvault.mixins.json` (created in M3):
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "io.github.silkypanda.blockvault.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": ["CommandSendMixin", "CommandsPacketMixin", "CommandHistoryMixin"],
  "injectors": { "defaultRequire": 0 }
}
```
Keep `JAVA_21` (it is a minimum level; do not raise it). Register the config in `fabric.mod.json` (`"mixins"`) and in `neoforge.mods.toml` (`[[mixins]] config = "blockvault.mixins.json"`). No refmaps (the game is unobfuscated). Add a startup self-check that logs one WARN line per hook that is not active (for example by having each mixin flip a static `boolean` when it first runs), so a broken hook after a Minecraft update is visible instead of silent.

### 8.3 Session wiring
`McSessionEnv implements SessionEnv`:
- `sendCommand`: on the game thread, send through the connection (verify the exact method), with the self-sent marker set.
- Vault access via `BackgroundExecutor`; results delivered back on the game thread.
- A master-password vault that is locked triggers the Unlock card first, then continues the pending action.
- `showSavePrompt` opens the card screen (section 8.5). Never open a card while another BlockVault card is open: queue it.
Config (`<dataDir>/config.json`, Gson tree API): `autofill` (true), `autofillDelayMillis` (400, minimum 200), `resultTimeoutMillis` (5000), `autoLockMinutes` (15, 0 = never), `cardDurationSeconds` (10), `cardPosition` ("top_right"), `streamerMode` (false), `generator` (policy fields), `scrubHistory` (true), `trustDevice` (true). Load lazily, write only when the user changes a setting.

### 8.4 Threading summary
Game thread: hooks, UI. Background thread: KDF, vault IO, key provider calls. Never block the game thread on either. Timers for the session use the game thread via `Minecraft.execute` + a scheduled executor shared with `BackgroundExecutor`.

### 8.5 The compact credential prompt
A small `Screen` subclass: transparent background (no dim, no blur; override background rendering and verify the 26.3 method), `isPauseScreen()` false, anchored to the configured corner. The card must occupy no more than 30% of the available screen area and should remain substantially smaller on ordinary screens. Click outside the card or Esc = "Not now". Enter = primary action.
Content only: variant title, one compact username/server context line (hide the address in streamer mode), masked password `EditBox`, optional email field only when required, and primary `Save`/`Update`/`Register` plus `Not now` actions. Include Reveal password and Copy password controls; registration prompts include Never save here. Cancel suppresses matching registration prompts until disconnect. Never save here persists the server policy until the player changes it in the vault. Rejected prompts include Manual, Update, Forget, Reveal password, and Not now. The whole card must remain within 30% of screen area. No bundled card/icon textures, logo, explanation text, countdown bar, or secondary menu. Use plain vanilla fills and widgets; every displayed string uses `Component.translatable("blockvault....")`.
Variants (one class, `CardVariant` enum): `SAVE`, `UPDATE` (shows masked old -> new), `REGISTER`, `REJECTED` (three actions: Enter manually, Update saved password, Forget).
Implementation constraints: vanilla widgets only (`Button`, `EditBox`), vanilla narration support, a plain vanilla fill with a violet accent, no bundled dialog textures or icon, and every string via `Component.translatable("blockvault....")`.
GUI rendering APIs were reworked repeatedly in the 26.x line: read the `Screen`/`GuiGraphics` dumps before writing any render code and follow whatever the real method names are.
Toasts for short confirmations ("Saved", "Autofilled", "Vault locked") use the vanilla toast system.

### 8.6 Vault screen
Keybind and multiplayer-screen button open it. List of servers (search box), per server: accounts, reveal, copy to the operating-system clipboard, edit, delete, generate new, edit command template, toggle autofill/neverSave. Once the vault is unlocked, these actions do not ask for the master password again. Footer: Lock now, Settings, Import, Export. Template selection includes automatic prompt detection and marks the selected option. Import accepts an encrypted vault file plus its master password and merges entries by host and modification time. Export writes a byte-for-byte encrypted copy to a path that must not already exist. If the main vault is missing or damaged, recovery lists encrypted backups and asks for the password that encrypted the selected backup before restoring it. Pagination and controls must stay within visible screen bounds; next/previous are clamped to valid pages.

### 8.7 Settings screen
Built on vanilla `OptionsSubScreen`/`OptionsList` (no config library). Options: autofill on/off, autofill delay, master-password prompt policy (`Ask every launch` or `Trust this device`, default trusted), auto-lock minutes, generator presets with descriptions, streamer mode, card position/duration, history scrub on/off, and the per-server autofill and Never save here policies. Each setting explains its effect on hover. Fabric: provide the settings screen through Mod Menu. For Minecraft 26.3 use the owner-supplied Mod Menu 21.0.0 artifact `maven.modrinth:mOgUt4GM:kyy7dbrZ` from `https://api.modrinth.com/maven`, as a plain `implementation` dependency; Minecraft 26.x is unobfuscated, and Loom 1.18.2 does not provide `modCompileOnly`. Mod Menu remains optional in `fabric.mod.json` and is not included inside the BlockVault jar. NeoForge: register an explicitly typed `IConfigScreenFactory` instance via the mod container to disambiguate its instance and supplier overloads.

### 8.8 Translations
`assets/blockvault/lang/en_us.json` is the sole locale file. Minecraft falls back to it for unsupported languages; do not add duplicate English locale stubs.

---------------------------------------------------------------------------
## 9. Test and quality requirements
---------------------------------------------------------------------------
- `core`: JUnit 5, all tests run in CI job `core`. No test may touch the real user data dir (use temp dirs and `blockvault.dataDir`).
- `shared-client` and loader code have no unit tests that need Minecraft (CI cannot launch the game). Keep logic in `core` so it is testable; keep `shared-client` thin.
- Every milestone: update `STATUS.md` (DONE/PARTIAL/TODO + "not verified in-game" list).
- Lint: no warnings in `core` (`-Xlint:all -Werror`).
- Review checklist before M6 is DONE: grep for logging calls near password variables; confirm `toString()` masking tests exist; confirm `check-rules.sh` passes; confirm jar sizes.

---------------------------------------------------------------------------
## 10. Things you MUST verify against real 26.3 sources (see rule 6)
---------------------------------------------------------------------------
1. Fabric Loom plugin id/version and that `dependencies { minecraft ... }` with no `mappings` is accepted.
2. MDG 2.0.148 DSL (`neoForge { version, runs, mods }`), and the template-based manifest expansion.
3. `@Mod(dist = Dist.CLIENT)` and constructor injection signature.
4. Names of: system-chat events (both loaders); join/disconnect events; `ClientPacketListener` command-send methods and commands-packet handler; `CommandHistory` API; `ServerData` icon accessor; `KeyMapping` constructor and category type; `Screen` render/background/input method signatures; `EditBox` formatter API; `ToastManager`/`SystemToast` API; `Minecraft#getCurrentServer`, `#getUser`, `#execute`.
5. Whether the NeoForge client needs a client-only marker to join vanilla servers.
Record outcomes in `DECISIONS.md`.

---------------------------------------------------------------------------
## 11. Explicitly out of scope
---------------------------------------------------------------------------
Releases and publishing of any kind; server-side component; cloud sync or accounts; telemetry; update checker; breach-check network calls; auto-update; support for other Minecraft versions or loaders; shading or bundling libraries.

---------------------------------------------------------------------------
## 12. Manual in-game test checklist (put this in README.md for the owner)
---------------------------------------------------------------------------
Test server: Paper + AuthMe (offline-mode or with a cracked-friendly setup), plus one with nLogin or LoginSecurity if available.
1. Fresh join -> register prompt -> BlockVault card shows a generated password -> confirm -> registered -> toast "Saved".
2. Leave and rejoin -> autofill sends `/login` once -> logged in.
3. Change the saved password in the vault manually -> rejoin -> wrong password message -> Rejected card appears, no retry loop.
4. Type `/login <right password>` manually -> Update card appears.
5. Check the game folder: typed login/register commands are NOT present in the command history file.
6. Kill the game right after `/register` -> relaunch -> vault shows the PENDING account (no lockout).
7. With `Trust this device` enabled (the default), unlock once with the master password, restart Minecraft, and confirm the vault opens without another prompt. Switch to `Ask for master password every launch`, restart, and confirm the password prompt returns.
8. Join a vanilla server (no login plugin) -> nothing happens, no log spam.
9. Join singleplayer -> nothing happens.
10. Both jars: Fabric and NeoForge behave identically.
11. Disconnect and watch `logs/latest.log`: no passwords, no secrets, no stack traces from BlockVault.
