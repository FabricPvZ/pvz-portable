# AGENTS.md — PvZ-Portable

Operating manual for anyone working in this repository (humans and AI alike): what the project is,
where the code lives, how to build/run/test it, and the rules to respect when committing
(**AI co-authorship** and **rebase-only Git history**). An external description lives in
[`README.md`](./README.md).

Conventions used below: `file:line` references are relative to this repository's root;
`../some-project/` is a **sibling repository** checked out next to this one (see §1); and
`../PvZ-Portable-Cpp/` is the read-only upstream C++ reference.

---

## 1. What this project is

- **A Java port of the upstream C++ project.** `../PvZ-Portable-Cpp/`
  ([wszqkzqk/PvZ-Portable](https://github.com/wszqkzqk/PvZ-Portable), a community C++20 / SDL2 /
  OpenGL ES 2.0 reimplementation of *Plants vs. Zombies* GOTY) is ported into a **pure Java desktop
  version** (JDK 25 + Gradle), using LWJGL 3.4.3's `lwjgl-sdl` (SDL3) for windowing/rendering/input
  and OpenAL + stb_vorbis for audio (MO3 through libopenmpt). Upstream is a **read-only reference**:
  this project neither builds nor modifies it.
- **It runs on the real Fabric Loader.** The game is plugged into Fabric Loader (Sponge Mixin
  included) through a `GameProvider` that is part of this project's own sources
  (`src/main/java/cn/enaium/fabricpvz/provider/`), and in the loader's eyes it
  is a built-in mod with mod id `pvz-portable` (`PvzGameProvider.java:80`). The `mods/` directory of
  the game directory is loaded by the loader, entry points are ordinary `ModInitializer`s, and the
  event/content APIs come from the `fabric-api` repository. The launch main class is the loader's own
  `net.fabricmc.loader.impl.launch.knot.KnotClient` (`build.gradle.kts:161`).
- **The game code itself has no Fabric dependency at all.** Inside `src/main`, `net.fabricmc` appears
  only in a javadoc comment (`src/main/java/cn/enaium/fabricpvz/portable/lawn/Main.java:10`). Only the
  provider package knows about Fabric: it finds the game, unlocks the class-loading path
  (so Mixin can rewrite game classes), runs the mod entry points and finally calls the game's main.
  Adding a `net.fabricmc.*` dependency inside `src/main` is an architectural change that needs
  explaining, not a convenience to help yourself to.
- **The game has no notion of mods and no content registry.** It exposes only hook call points, which
  it calls at its own hook sites (`src/main/java/…/lawn/hooks/LawnHooks.java`; the API's bridge turns
  each call into an event), and definition tables sized by `SeedType.MAX_SEED_TYPES=200` /
  `ZombieType.MAX_ZOMBIE_TYPES=100`, indexed by id, where an empty row means "not registered"
  (`Plant.gPlantDefs` / `Zombie.gZombieDefs`; `getPlantDefinition(int)` / `getZombieDefinition(int)`
  return `null` for an empty row or an out-of-range id). Custom plants and zombies are registered by
  `fabric-api`'s `api-plant`/`api-zombie`, whose Mixins inject the definition's behaviour into the
  game's methods; the seed chooser and the Almanac both show registered content on a page after the
  vanilla grid (page 0) — a registered seed the player has unlocked, and, on the Almanac's zombie page,
  every defined zombie type above the retail range.
- **Sibling repositories.** The game is its own repository (`FabricPvZ/pvz-portable`, `main` only).
  The Fabric-aware pieces are separate repositories that are *dependencies* of this one:

| Sibling repository | Role |
|---|---|
| `../fabric-api/` | the mod API: `api-base`, `api-events`, `api-plant`, `api-resource`, `api-zombie` (**compiled against this project**, a reverse dependency by Fabric semantics) |
| `../loom/` | the mod toolchain Gradle plugin `cn.enaium.fabricpvz.loom`: one `plugins { }` line gets a mod every dependency plus `runClient` |
| `../pvz-example-mod/`, `../pvz-content-mod/`, `../speed-mod/`, `../auto-collect-mod/` | four real mods (example / content / speed / auto-collect) |
| `../PvZ-Portable-Cpp/` | the upstream C++ source, read-only reference, not part of the build |

- **Licence**: LGPL-3.0-or-later, with `LICENSE`, `COPYING`, `NOTICE` and
  `SexyAppFramework-LICENSE.txt` in this repository. The sibling repositories are MIT.
  **This repository contains no PopCap/EA assets**; runtime assets are supplied by the user
  (`README.md`, "Game assets").

### The three-layer code structure

The three packages under `src/main/java/cn/enaium/fabricpvz/portable/` correspond to upstream's three
layers; before changing anything, work out which layer you are in:

| Package | Upstream counterpart | Contents | File count |
|---|---|---|---|
| `sexy/` | `src/SexyAppFramework/` | the engine: Widget/Dialog, the app main loop, Graphics/Image/Font, PAK/assets, audio, the platform layer | 93 (including `sexy/graphics` 19, `sexy/parsers` 8) |
| `lawn/` | `src/Lawn/` | the game layer: Board/Plant/Zombie/Projectile/Coin, LawnApp and the screens, save data, challenge mode | 180 (including `lawn/widget` 27, `lawn/system` 10) |
| `pvzp/` | `src/PvzpLib/` | the portable runtime: reanim, fx/particle, audio decoders, asset/PAK/cache, the SDL platform implementation | 102 (including `pvzp/fx` 32, `pvzp/reanim` 18, `pvzp/audio` 17) |

(375 `src/main` java files and 149 `src/test` java files across the three layers; counted with
`find … -name '*.java' | wc -l` on the package directories.)

**Porting traces must be kept**: every ported type/method records its upstream origin in javadoc, in
the form `Ported from src/Lawn/Board.cpp:246 (BoardInitForPlayer)`. 355 of the 375 files in `src/main`
carry such a marker (about 2358 of them in total). New or changed ported code has to state the
`src/...:<line number>` and keep the line number in step with `../PvZ-Portable-Cpp/` — this is the only
evidence chain in this repository for "why does it look like this". Behaviour that deliberately
deviates from upstream is marked `PORT-NOTE …` in a comment next to the code (`LawnApp.java:523,572`,
`PvzpStringFileTest.java:201`), and the deviation log in `README.md` collects the user-visible ones.

---

## 2. What matters in the directory

```
PvZ-Portable/
├── build.gradle.kts            the build script (version, dependencies, test config, driver tasks, publishing)
├── settings.gradle.kts         repository sources: mavenLocal() + mavenCentral() + Fabric maven
├── gradle/libs.versions.toml   version catalog: lwjgl 3.4.3 / junit 6.1.3 / fabric-loader 0.19.5 / slf4j-api 2.0.20
├── src/main/java/…             the three layers of source (above), with no resources directory at all: no fabric.mod.json in the game jar
├── src/test/java/…             tests mirroring main's package tree (sexy/ lawn/ pvzp/), plus two test-only packages:
│                               test/ (shared fixtures and the driver tasks) and contentmodfixture/ (the loader end-to-end fixture)
├── src/test/resources/junit-platform.properties   the 180s timeout for all tests (SEPARATE_THREAD)
├── run/                        the game dir: run/mods/ holds mods, run/.fabric/ is the loader cache, run/userdata/ holds save data
├── build/                      build outputs: libs/, distributions/, install/, test-results/, testmods/, window-dump/
├── userdata/, cache64/         user data written when you run the game straight from the project directory
└── README.md, LICENSE, COPYING, NOTICE, SexyAppFramework-LICENSE.txt
```

- **The game dir is `run/`**: the `run` task sets the working directory to `run/` and passes
  `-Dpvz.gameDir=<project>/run` (`build.gradle.kts:458-465`). `mods/` and `.fabric/` both live under
  it; the provider resolves the game directory in the order `-Dpvz.gameDir` → the `-gamedir <path>`
  command-line flag → the current working directory (`PvzGameProvider.java:240-251`). **The build puts
  nothing into `run/mods/`**: what the loader loads there is what a player (or a developer) copied in,
  exactly as in an installation.
- **Nothing under `run/` or `build/` goes into git** (`.gitignore`: `run/`, `build/`, `userdata/`,
  `cache64/`, `.idea/`). Do not commit the user data or logs that tests and runs produce. `run/mods/`
  is a real installation's mods directory, and no task of this build reads or writes it.
- **`build.gradle.kts` is this project's hub**: the dependencies (the launch libraries, the test-only
  `modsForTest` configuration — the API's aggregate jar and the example mod, which the loader
  end-to-end test loads from a real `mods/` directory — and the API, which is **not** a dependency of
  the game in either direction: it is a mod, so this project consumes the published mavenLocal
  coordinates of `apiVersion`, `:68`, and only `javac` sees its classes, through `testCompileOnly`),
  the shape of the distribution and all the driver tasks live in it — read it through before changing
  any behaviour.

---

## 3. Building, running, testing

Environment: **JDK 25** (toolchain and `--release 25`, `build.gradle.kts:24-26,275`), **Gradle 9.8.1**
(pinned by the wrapper, `gradle/wrapper/gradle-wrapper.properties`), and Kotlin only for mods.
`gradle.properties` sets `org.gradle.java.installations.auto-download=false`, so JDK 25 has to be
installed on the machine already.

This project is standalone: it consumes the artifacts of its sibling repositories as **mavenLocal
coordinates** — the mod API (its five `api-*` modules for the
test compile class path, its aggregate jar for the loader end-to-end test's `mods/` directory) and the
example mod — never as Gradle subprojects and never as files of a sibling checkout. The mod Gradle
plugin `cn.enaium.fabricpvz.loom` that the API build applies comes from the **Gradle Plugin Portal**,
so it needs no checkout and no local publication here. A first-time setup publishes them in this
order:

```bash
# this project, without tests: the API compiles against this game's coordinate, so its POM must exist first
./gradlew publishToMavenLocal -x test
# from ../fabric-api (publishes the aggregate `fabric-api` jar and the five `api-*` modules)
./gradlew publishToMavenLocal
# from ../pvz-example-mod (the loader end-to-end test loads its jar into a real mods/ directory)
./gradlew publishToMavenLocal
# now this project builds, tests and publishes in full
./gradlew build publishToMavenLocal
```

**`apiVersion` (`build.gradle.kts:68`, currently `1.2.0`) is the API release this project's tests are
written against**: the test fixtures compile against
`cn.enaium.fabricpvz:api-base|api-events|api-plant|api-resource|api-zombie:<apiVersion>`, and the
loader end-to-end test puts `cn.enaium.fabricpvz:fabric-api:<apiVersion>` into its `mods/` directory.
After changing the API, publish it again before running this project's tests — a stale mavenLocal
publication is the one failure mode that reads as a broken test rather than as a missing step:

```bash
(cd ../fabric-api && ./gradlew publishToMavenLocal)
cd ../PvZ-Portable && ./gradlew test
```

Day to day, inside this project:

```bash
./gradlew build                # compile + all tests (this suite is the bar, see §4)
./gradlew test                 # tests only
./gradlew test --tests 'cn.enaium.fabricpvz.portable.lawn.BoardTest'
./gradlew publishToMavenLocal  # publishes cn.enaium.fabricpvz:pvz-portable:<version>
./gradlew run                  # starts the game on the real Loader (working directory run/; the mods come from run/mods/)
./gradlew syncDevResources run -PpvzResDir="/path/to/resources"   # copy main.pak/properties into run/, then start
```

There is no API check task: the API is an ordinary mavenLocal coordinate, so a missing or stale
publication fails as an unresolved dependency (or as a test that does not match the API's working
tree), and the fix is always `(cd ../fabric-api && ./gradlew publishToMavenLocal)`.

- `run` passes exactly the same arguments as a mod development run: `-Dpvz.gameDir=run`,
  `-Dfabric.skipMcProvider=true`, `-Dfabric.development=true` (a development *environment*: the loader
  also discovers mods that sit on the class path), `--enable-native-access=ALL-UNNAMED`, plus
  `-XstartOnFirstThread` on macOS (`:458`). Its class path is the application's own — game, provider
  and the provider's runtime libraries; no mod is on it. The mods a run loads are the jars in
  `run/mods/`, put there by hand: `run` copies nothing in (§5).
- For the asset discovery order (and the meaning of `-savedir`) see `README.md`; the code is
  `pvzp/ResourceLocator.java:13-30`. `syncDevResources` (`:441`) copies only `main.pak` and
  `properties/**`, and it is a task of its own — `run` does not depend on it (verified with
  `./gradlew run --dry-run -PpvzResDir=…`, whose graph is `compileJava`/`processResources`/`classes`/
  `run`), so pass it explicitly:
  `./gradlew syncDevResources run -PpvzResDir=…`.

---

## 4. The test system: the game suite is the bar

**Any change that touches this project has to clear `./gradlew test` green.** The suite holds **1458**
`@Test`/`@ParameterizedTest` cases in **145** test classes (0 failures, 0 errors, 0 skipped). The
number moves as tests are added — trust the count your own run reports.

The stack and the global conventions: JUnit 6.1.3 (Jupiter, `useJUnitPlatform()`,
`build.gradle.kts:291`); every test has a 180-second wall-clock budget with timeouts in
`SEPARATE_THREAD` mode (a test that hangs fails instead of hanging the whole build,
`src/test/resources/junit-platform.properties`); the test JVM runs with `SDL_VIDEODRIVER=dummy` and
the software renderer, plus `-XstartOnFirstThread` on macOS.

### How tests are written in each of the three layers

| Kind | Location | How it is written |
|---|---|---|
| Pure unit tests | the mirrored package tree: `src/test/java/…/{sexy,lawn,pvzp}/**` | construct objects directly; when a downstream service needs `LawnApp`, use the fixtures below |
| The headless fixture | `src/test/java/…/test/HeadlessTestApp.java` | after `new LawnApp()` you must call `HeadlessTestApp.initialize(app)`: the asset tables are filled with 1×1 stub images and stub bitmap fonts and audio is swapped for `StubSoundManager`, so **no real assets are needed** (without it, entity constructors null-pointer) |
| Domain fixtures | `lawn/LawnEntityTestSupport.java` (`TestLawnApp` + `StubReanimation`/`StubZombie`), `pvzp/reanim/ReanimTestSupport.java`, `pvzp/audio/TestAudio.java` (builds WAVs in memory) | assert grids/state machines/matrix blits and the like, avoiding a dependency on retail art |
| Tests that need real assets | the whole repository | take assets through `pvzp/TestAssets.java` (`-Dpvz.test.resources=<dir>` or the standard discovery chain), and they **must** be gated with `Assumptions.assumeTrue(...)` so the suite stays green on machines without the game installed |
| Loader end to end | `src/test/java/…/contentmodfixture/FabricLoaderEndToEndTest.java` | see below |

### Loader end to end (`FabricLoaderEndToEndTest`)

It uses a **second JVM** whose main class is the loader's `KnotClient`, puts the game / loader
(Mixin+ASM) / provider on the class path — **not** the API — and installs three mod jars into a **real
`mods/` directory**: the API's aggregate jar and the example mod (Gradle syncs both into
`build/testmods/` through `testModsJar`, `:149`, and hands the directory to the test through the
`pvz.test.modsDir` system property) and a fixture mod the test packs on the spot. With no API on the
class path, both loader runs (development mode and the installation shape) resolve it the way the
loader always does: `fabric-api` plus the five modules nested in its jar. Because the loader defines
its own copy of the game/API classes, **assertions cannot read static state off those classes**: the
fixture writes its observations into a report file and the test asserts by reading the file (the
loader version, the resolved mod list, whether Mixin was injected into game classes, the API hook
chain, and whether a real level can reach `SCENE_PLAYING`). It also asserts the game version the loader
reports (`FabricLoaderEndToEndTest.java:113`). This test **requires
`cn.enaium.fabricpvz:fabric-api:<apiVersion>` and `cn.enaium.fabricpvz:pvz-example-mod:1.1.0` to be in
mavenLocal already** (the `publishToMavenLocal` of §3, from `../fabric-api` and `../pvz-example-mod`);
forget either and it fails as an unresolvable coordinate.

### Real-window/driver tasks (not part of `test`, run them by hand)

| Task | Driver class | Purpose |
|---|---|---|
| `./gradlew dumpWindow` | `test/WindowDump.java` | opens a window on a real video driver and writes the window contents to `build/window-dump/frame-NNN.png` every 0.5s; `-Ppvz.renderer=software\|metal\|opengl` (`:314`) |
| `./gradlew zoomDump` | `test/AwardPickupDump.java` | the frames around the moment the level-reward seed packet (scaled up to 2×) is picked up, plus a filtering probe for the zoomed drawing (`:366`) |
| `./gradlew playthrough` | `test/AdventurePlaythrough.java` | plays adventure mode end to end from the menu using **synthetic SDL events** (through the real input path), `-Ppvz.play.firstLevel/lastLevel/firstMode/lastMode/tickBudget/fast/fromMenu/logClicks` (`:389`) |
| `./gradlew sceneDump` | `test/SceneDump.java` | frames of one rendering scenario, for looking at a specific animation frame by frame: jumps into the level the scenario needs and dumps the window; `-Ppvz.dump.scenario=graveRise\|fume\|packet\|zomboni` (`:342`). Two traps it already solves: `TitleScreen` never advances unless `LawnApp.loadingCompleted()` is called, and the level intro must be fast-forwarded *after* `startPlaying()` (`mCutsceneTime = 100000`, then tick) or the seed bank never slides in. A third: that jump also skips the intro's exact-equality windows, so the `zomboni` scenario plays the intro at the game's own pace instead (`SceneDump.playIntro`) |
| `./gradlew bench` | `test/FrameBench.java` | fps / draw time / GC of the real loop, `-Ppvz.bench.seconds/level/zombies/cpuFrames/iterations/jfr` (`:416`) |

Three hard rules when writing such a driver (the drivers' own headers say the same):

1. reading the window contents back must happen **before** `SDL_RenderPresent` (after present the back
   buffer is undefined, and on Metal the read-back is blank);
2. the read-back must be on the frame-loop thread (the SDL renderer is not thread-safe; reading it
   from another thread aborts on Metal);
3. the "cheats" a driver uses (instakilling zombies, compressing the wave countdown) must live in the
   driver's own hook listeners and **must not touch game code**.

### One pitfall worth knowing up front

The window's **logical size** (used for layout, e.g. 800×600) and its **drawable size** (used for
rendering, 1600×1200 on Retina) are two different things: `WINDOW_RESIZE` carries only the logical
size, and scaling is driven by `RendererBackend.renderOutputWidth/Height`; feed the drawable size back
in as the window size and the symptom is "the window displays nothing".

---

## 5. Mods and the surface this project exposes

- **The stable surface is the mod id `pvz-portable`**: a mod writes `"pvz-portable": "*"` in its
  `fabric.mod.json`, so a new version of the game does not require mod changes. Mods are built by the
  `loom` repository's plugin, published on the Gradle Plugin Portal
  (`plugins { id("cn.enaium.fabricpvz.loom") version "<version>" }` — a plugin applied from the Portal
  needs its version stated); the loader/Mixin/ASM versions come from one place,
  `../loom/src/main/kotlin/cn/enaium/fabricpvz/loom/PvzToolchain.kt` — do **not** hand-write those
  coordinates in a mod's build. The version a mod states has to be resolvable: the workspace is on
  1.0.1 (the release whose `runClient` loads the mod from the class path), which the portal does not
  serve yet, so a mod's `settings.gradle.kts` lists `mavenLocal()` before `gradlePluginPortal()` and
  `(cd ../loom && ./gradlew publishToMavenLocal)` is the step that provides it.
- **How a mod gets the game**: the `pvz(...)` configuration names a published version of this project,
  and the toolchain downloads that version's GitHub Release distribution zip (asset name
  `pvz-portable-<version>.zip`) and puts the jars from its `lib/` onto the mod's compile/test/
  `runClient` class paths — **the distribution is itself the launch class path**. For local work the
  same configuration can point at `../PvZ-Portable/build/distributions` or an unpacked zip. This is
  why **the composition of `lib/` in this project is a public interface**: adding or removing a
  dependency here changes what every mod links and runs against.
- **How the API reaches a run**: it is always a mod and never a plain library. In an installation, and
  in this project's own `./gradlew run`, the loader reads it from the game directory's `mods/`
  directory, where a person puts it: `./gradlew run` copies nothing in (the build has no such task any
  more, §3), and an installation is a `mods/` directory next to `bin/` (§6). A **mod's** `runClient`
  (the `loom` plugin's task, §5's first bullet) is a *development* run: it passes
  `-Dfabric.development=true` and puts the mod's own output (its classes and resources directories, as
  one `fabric.classPathGroups` entry) *and* `cn.enaium.fabricpvz:fabric-api` on the class path, where
  the loader discovers both — the five feature modules come from the jars nested inside that one
  aggregate jar. That run writes nothing into `run/mods/` and does not read it either (the directory
  stays what it is in an installation: a place to drop another mod, e.g. a jar a player installs); a
  jar of the mod under development left there by an older toolchain has to be deleted, because the
  loader resolves two candidates for the same mod id to one of them without complaining — measured on
  fabric-loader 0.19.5 it takes the one in `run/mods`, i.e. the stale jar silently wins over the
  class path copy the run was supposed to show. The one API jar to copy into a `mods/` directory is
  `fabric-api-<version>.jar`, which carries the five feature modules nested inside it.
- **Mods bundle no dependencies at all** (a duplicate is a bug): the loader, Mixin, ASM and the API
  come from the launch class path, and nothing else does. Kotlin mods compile against `kotlin-stdlib`
  without bundling it — the runtime copy they link against is the one nested in the
  `fabric-language-kotlin` language module, which the toolchain brings and the mod declares. Such a
  mod must declare its entry points as `class`es, not `object`s: Fabric Loader instantiates them
  through a no-argument constructor, so an `object` would have to name the `"kotlin"` language adapter
  that `fabric-language-kotlin` provides.

---

## 6. The version number, the distribution, releases

- **The version is stated by two lines that go together**: `build.gradle.kts` (`version = "1.2.5"`) and
  `PvzGameProvider.DEFAULT_GAME_VERSION`. It is this repository's version and it does **not** track the
  upstream C++ project's release tags.
- The same `project.version` decides four things: the jar name (`base { archivesName = "pvz-portable" }`,
  `:21`; `build/libs/pvz-portable-<version>.jar`), the Maven coordinate
  `cn.enaium.fabricpvz:pvz-portable:<version>` (published by `publishToMavenLocal`, with
  LGPL-3.0-or-later POM metadata), the distribution zip's file name (`:201`), and
  `Implementation-Version` in the jar manifest — i.e. the version the loader reports for the built-in
  `pvz-portable` mod through the provider. When changing the version, change the `version` line of
  `build.gradle.kts` *and* `PvzGameProvider.DEFAULT_GAME_VERSION` together (both state it); never
  hard-code a version string anywhere else.
- **The distribution is produced by Gradle's `application` plugin**: `./gradlew distZip` →
  `build/distributions/pvz-portable-<version>.zip`, and the layout is **one zip for every platform,
  with no mod in it**: the `bin/pvz-portable` launcher script (plus `.bat`), and `lib/*.jar` (the game
  jar + the loader/Mixin/ASM/logging stack the provider brings + the LWJGL natives of Windows, Linux
  and macOS on x64 and arm64, `distributionNativePlatforms` at `:46`). There is **no `mods/` directory
  and no mod jar**: mods are what the player installs, so create the directory at the unpacked root —
  **`mods/` sits next to `bin/`**. The script carries `--enable-native-access=ALL-UNNAMED` and
  `-Dfabric.skipMcProvider=true` (`applicationDefaultJvmArgs`, `:211`), and it appends two more things
  to its own `DEFAULT_JVM_OPTS`: `-XstartOnFirstThread` on macOS only, and
  `-Dpvz.gameDir=$APP_HOME` / `%APP_HOME%`, i.e. the unpacked root the script itself lives in — so
  **`mods/` loads whatever the caller's working directory is** and a user-supplied `-Dpvz.gameDir`
  (`JAVA_OPTS`/`PVZ_PORTABLE_OPTS`, evaluated after `DEFAULT_JVM_OPTS`) overrides that default. The
  provider's `-gamedir` argument is shadowed in a distribution, because the property is resolved first.
  `./gradlew installDist` expands the same thing into `build/install/pvz-portable/`.
- **`lib/native/` is the native library directory of the distribution**: the `libopenmptNatives` task
  (`:201`) downloads the pinned official libopenmpt Windows archive
  (`libopenmpt-0.8.9+release.dev.windows.vs2022.zip`, `libopenmptVersion`/`libopenmptArchiveSha256` at
  `:93`, sha256-verified, cached in `<gradle user home>/caches/pvz/libopenmpt`) and stages
  `lib/native/<os>-<arch>/` with the five DLLs of each platform (`libopenmpt.dll` + the codec DLLs it
  is linked against — they are one unit, see `OpenMptLibrary`), the BSD-3-Clause licence and a manifest
  of the archive and every file's sha256.
  `distZip`/`installDist` carry it (`distributions` at `:427`: `lib/native/` next to the jars) and
  `run` stages it in `run/lib/native/` (`libopenmptRunNatives` at `:725`), so the development game
  directory has the layout a release has. **No binary is committed**: the archive is a build-time
  download and a build without network and without the cache fails with the URL instead of producing a
  zip without the libraries. Upstream publishes compiled binaries for Windows only
  (lib.openmpt.org/libopenmpt/download points every other system at its distribution packages), so
  macOS and Linux have no packaged directory and keep the system lookup; `OpenMptLibrary` probes
  `<gameDir>/lib/native/<os>-<arch>/` and `<gameDir>/native/<os>-<arch>/` first, which is also the
  place a user-supplied library goes. The system lookup covers the distribution directories of Linux
  (`/usr/lib64`, `/usr/lib`, `/lib64`, `/lib`, the Debian/Ubuntu multiarch triplets) under both the
  unversioned name and the soname (`libopenmpt.so.0` — Fedora's `libopenmpt` package ships no
  unversioned file, so without the soname an installed library was never found). The `test` task
  depends on `distZip` (`:559`):
  `DistributionNativeLibrariesTest` asserts the zip's native entries by name and sha256.
- **Releases are cut by hand; this repository has no CI automation** (there is no workflow, and
  nothing runs on a push). From the tagged commit: `VERSION="$(./gradlew -q printVersion)"`,
  `git tag -a "$VERSION" -m "pvz-portable $VERSION" && git push origin "$VERSION"`,
  `test "${VERSION#v}" = "$(git describe --tags --exact-match)"`, `./gradlew distZip`, then
  `gh release create "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" --title
  "pvz-portable $VERSION" --verify-tag` (re-cutting an existing release is
  `gh release upload "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" --clobber`). The
  release's asset name is derived from `project.version`, so **the tag has to be the version**: that
  `test` is what keeps the two in agreement, and the `printVersion` task (`:266`) is the version
  reader the steps use. The zip does not depend on the machine that built it, because `lib/` carries
  the natives of every platform. The 1.0.0 release was cut this way. Leave packaging to Gradle's
  `distZip`, never hand-roll an archive, and do not add a workflow back: a release is a deliberate,
  local act.
- **The places to keep in sync when you change the version** (miss one and a mod build resolves the
  wrong game, silently):

| Location | Why |
|---|---|
| `build.gradle.kts:15` | the single declaration point (jar / POM / distribution / manifest all take `project.version`) |
| `../fabric-api/{api-base,api-events,api-plant,api-resource,api-zombie}/build.gradle.kts` | each module pins `cn.enaium.fabricpvz:pvz-portable:<version>` once for `compileOnly` and once for `testImplementation` |
| `../loom/src/main/kotlin/cn/enaium/fabricpvz/loom/PvzToolchain.kt:31,34` (`GAME_VERSION`, `GAME`) | the default of the mod-side `pvz(...)` configuration; see §5 |
| `src/main/java/…/provider/PvzGameProvider.java:89` (`DEFAULT_GAME_VERSION`) | the version of the built-in `pvz-portable` mod as the loader sees it |
| `src/test/java/…/contentmodfixture/FabricLoaderEndToEndTest.java:113` | the end-to-end test asserts the game version the loader reports |
| each mod's `fabric.mod.json` | **no change needed**: they write `"pvz-portable": "*"` (version-independent) |

---

## 7. Development guidelines

1. **Find the upstream counterpart before writing code.** This project is a port, not a
   from-scratch design: locate the `file:line` under `../PvZ-Portable-Cpp/src/...`, write
   `Ported from …` in the javadoc, and when the behaviour deviates, write the reason down as a
   `PORT-NOTE` next to the code and (for user-visible deviations) in `README.md`'s deviation log —
   for example Java has no non-UTF-8 bytes, or the differences between SDL3 and SDL2.
2. **Follow the existing style and naming.** Ported code deliberately keeps upstream's style:
   `theXxx`/`aXxx` parameter names, `mMember` field names, and definition-table/enum order matching
   upstream (changing the order gets caught by the tests that compare item by item). Don't casually
   reorder, rename or bulk-reformat.
3. **Do not introduce Fabric/Loader dependencies into game code** (a `net.fabricmc.*` appearing in
   `src/main` is a design change), and don't give the game a second logging implementation: the logging
   stack (Log4j + `log4j2.xml`) is part of this project's launch libraries (the `runtimeOnly`
   dependencies and the resource), and this project declares only the SLF4J API (`api(libs.slf4j.api)`).
4. **Logging goes through `Common`**: `Common.logInfoLn/logErrorLn/logWarnLn/logDebugLn`
   (`src/main/java/cn/enaium/fabricpvz/portable/sexy/Common.java:187`, the logger name is exactly
   `pvz-portable`, `:99`); do not add `System.out.println`/`printStackTrace`.
5. **New behaviour comes with tests and changed behaviour changes tests; the bar is `./gradlew test`
   green.** Tests that need `LawnApp` should use the fixtures from §4; assertions that need real assets
   must always be gated with `Assumptions`. After fixing a regression, prefer adding a test that
   reproduces it.
6. **Do not commit build/run data**: `build/`, `run/`, `userdata/`, `cache64/`, `*.log`, `.idea/`
   (`.gitignore`). Don't add asset files (`main.pak`, `properties/`) to the repository either.
7. **Do not modify `../PvZ-Portable-Cpp/`** (read-only upstream), and don't change single-owner areas
   of the sibling repositories just to make tests pass — say that someone else needs to make a change
   instead of slipping it in.

---

## 8. AI collaboration and commit rules

**AI-written changes are welcome here** (a good deal of this project's code and documentation was
produced with AI involvement). The only requirement is that **the AI is credited as a co-author at
commit time**, while the human remains the author.

### AI credit in the commit message (required)

Write the trailer as the **last paragraph** of the commit message (separated from the body by one blank
line):

```
Co-Authored-By: <name> <email>
```

- One line per AI that took part in that commit; several AIs means several lines.
- Use the identity the tool actually uses (name and email); don't invent an email that doesn't exist, or
  GitHub won't recognise it and won't display the AI as a co-author. For example Claude Code uses
  `Co-Authored-By: Claude <noreply@anthropic.com>`; for another tool, use the identity its documentation
  gives.
- **The human author is still the author**: don't set the AI as the author with
  `git commit --author="Claude …"`, and don't change the committer; co-authorship shows up only in the
  trailer.
- Don't add `Signed-off-by` on the human author's behalf (a DCO sign-off has to be confirmed by that
  person).

Example (one AI involved):

```
lawn: fix SeedPacket being clipped 2px off the right edge in the seed chooser

The card face is now centred on the logical width instead of computing the offset from the drawable
width; adds a pixel-level regression test.

Co-Authored-By: Claude <noreply@anthropic.com>
```

The commit message itself should say what changed and why, with a one-sentence imperative subject;
include the relevant upstream `file:line`.

### Git history rule: rebase only

History allows **rebase-style integration only**; merge commits are not allowed:

- **No** `git merge`, and no "Create a merge commit" on a PR. Integrate with a rebase (or rebase and
  squash into clean commits).
- **Rebase onto the latest `main` before pushing**, to keep the history linear. Suggested local
  configuration: `git config pull.rebase true`, `git config merge.ff only`.
- **Do not rewrite history with `git revert`-style operations.** In a rebase-only workflow, "undo" means
  **fixing forward**: open a new commit that makes the wrong thing right, and name the commit it corrects
  in the message (for example `fix: use the right width for the seed-packet coordinates in 3f2a1c0`).
  That keeps history linear and every commit readable.
- Only **your own, not-yet-pushed** branches may be rewritten with `git rebase -i`
  (`drop`/`edit`/`reword`/`squash`).
- **Never rewrite published history**: don't push to a shared branch (`main`, someone else's branch, any
  commit that has already been pushed) with `--force`. If you genuinely need to, coordinate with the
  maintainer first, use `--force-with-lease`, and only on your own feature branch.

This repository is `git@github.com:FabricPvZ/pvz-portable.git` (hosted by the **FabricPvZ**
organization, `main` only), so the rules above are the rules of this project itself.

---

## 9. References

- `README.md`: what the project is, requirements, build/run/test, asset discovery, the distribution and
  how mods are installed, the porting status matrix, the deviation log, the driver tasks and the licence.
- `GAME-PROVIDER.md`: how the loader finds the game, the full table of launch arguments and
  system properties, the logging stack (the game provider is part of this project,
  `src/main/java/cn/enaium/fabricpvz/provider/`).
- `../loom/README.md`: what the mod toolchain supplies, what `runClient` does (a development run: the
  mod's own output and the API on the class path, nothing copied into `run/mods/`), the Kotlin rules, and how
  a mod's `pvz(...)` configuration resolves this game's distribution.
- The FabricPvZ documentation (kept outside this repository, next to the sibling checkouts):
  `../docs/` is the mod development site (VitePress 2, Chinese and English: getting started, project
  structure, Mixin experiments, the content/resource APIs, the `pvz-content-mod` walkthrough, building
  and publishing, and the API reference), and `../verification/` holds the internal records
  (`VERIFICATION.md`, the round-by-round verification log, and `research/`, the upstream specs) — read
  the latter to find out how a behaviour "was verified back then". If a change here affects the game's
  public API, the snippets in the site's chapters may need updating there.

---

## 10. This file's sources of truth

The structural statements above come, one by one, from the files below (paths relative to this
repository unless they start with `../`, which means the sibling checkout); values that move, such as
test counts, note how they were observed:

| Statement | Files checked |
|---|---|
| project identity, the three-layer structure, licence, the role of each sibling repository | `README.md`, `NOTICE`, `LICENSE`, `COPYING`, `git remote -v` |
| build configuration, dependencies, distribution, driver tasks, version declaration | `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties` |
| entry points and logging conventions | `src/main/java/…/lawn/Main.java`, `src/main/java/…/sexy/Common.java` |
| the game-side hook points and the capacity-sized definition tables | `src/main/java/…/lawn/hooks/LawnHooks.java`, `src/main/java/…/lawn/SeedType.java` (`MAX_SEED_TYPES`), `src/main/java/…/lawn/ZombieType.java` (`MAX_ZOMBIE_TYPES`), `src/main/java/…/lawn/Plant.java` / `Zombie.java` (`gPlantDefs`/`gZombieDefs`, `getPlantDefinition`/`getZombieDefinition`) |
| game dir / mod loading | `GAME-PROVIDER.md`, `src/main/java/…/provider/PvzGameProvider.java`, the actual contents of `run/` |
| the test system and fixtures | `src/test/java/…/test/*.java`, `src/test/java/…/contentmodfixture/*.java`, `src/test/java/…/pvzp/TestAssets.java`, `src/test/resources/junit-platform.properties`, `build/test-results/test/*.xml` |
| asset discovery and Steam lookup | `src/main/java/…/pvzp/ResourceLocator.java`, `src/main/java/…/pvzp/SteamLibraryFinder.java` |
| the mod toolchain and the mod projects | `../loom/src/main/kotlin/…/PvzToolchain.kt`, `../loom/src/main/kotlin/…/PvzModPlugin.kt`, each mod's `fabric.mod.json`, `../fabric-api/settings.gradle.kts` + each mod's `settings.gradle.kts`/`build.gradle.kts` (the `pluginManagement` block that resolves the plugin from `mavenLocal` first and the Gradle Plugin Portal after it, and the version stated there) |
| the release process | `README.md`'s "Releases" (cut by hand: `printVersion`, `git tag`, `distZip`, `gh release create`), `build.gradle.kts`'s `printVersion`/`distZip` |
| how the API modules pin the game coordinate | `../fabric-api/{api-base,api-events,api-plant,api-resource,api-zombie}/build.gradle.kts` |
| the upstream version (which this project's version does *not* follow) | `../PvZ-Portable-Cpp/CMake/ProjectVersion.cmake` and its git tag |
