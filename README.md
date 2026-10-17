# PvZ-Portable (Java)

A **Java desktop port** of [PvZ-Portable](https://github.com/wszqkzqk/PvZ-Portable) — the community
C++20 / SDL2 / OpenGL ES 2.0 reimplementation of *Plants vs. Zombies* GOTY — running on the **real
[Fabric Loader](https://github.com/FabricMC/fabric-loader)** (Sponge Mixin included) through a game
provider, so the game is extended exactly like a Minecraft Fabric mod: `fabric.mod.json`,
`ModInitializer` entry points, a `mods/` directory, Sponge Mixin, and an event bus the game triggers at
its own hook points.

The version is **1.1.0**: the Gradle build, the jar, the Maven coordinate
(`cn.enaium.fabricpvz:pvz-portable:1.1.0`) and the release archive
(`pvz-portable-1.1.0.zip`) all carry it. It does **not** track the upstream C++ project's release tags.

The port has three layers, mirroring upstream: the engine (`sexy/`, the equivalent of
SexyAppFramework), the game (`lawn/`, the equivalent of upstream's `src/Lawn/`) and the portable
runtime (`pvzp/`, the equivalent of `PvzpLib`). It uses **LWJGL 3.4.3's `lwjgl-sdl` (SDL3)** for
windowing, rendering and input, and OpenAL + stb_vorbis for audio (MO3 through libopenmpt).

The game code itself has **no Fabric dependency at all**: Fabric Loader is the launcher, and in the
loader's eyes the game is a built-in mod with mod id `pvz-portable`. Everything Fabric-specific — the
`GameProvider`, the launch libraries, the event/content API, the mod Gradle plugin — lives in the
sibling projects listed under [Related projects](#related-projects).

**This repository ships no game assets.** *Plants vs. Zombies* is copyright PopCap/EA, and the game
reads the retail resources from a directory you point it at (or from your local Steam install) — see
[Game assets](#game-assets-user-provided).

## Requirements

| Component | Version | Notes |
|---|---|---|
| JDK | **25** | Gradle toolchain pinned to 25, sources compiled with `--release 25` |
| Gradle | **9.8.1** | pinned by the wrapper — `./gradlew` is enough, no preinstalled Gradle |
| Platform bindings | **LWJGL 3.4.3**: `lwjgl-sdl` (SDL3), `lwjgl-opengl`, `lwjgl-openal`, `lwjgl-stb`, `lwjgl-nfd` | only LWJGL 3.4.x has SDL3 bindings (3.3.x publishes no `lwjgl-sdl`) |
| Audio (optional) | `libopenmpt` | needed for MO3 music only. The distribution ships the Windows build of it and the port looks there first; macOS/Linux use the installed/system copy. Without any copy music is disabled and sound effects work normally |
| macOS | `-XstartOnFirstThread` | SDL needs the JVM's main thread to own the first thread; the `run`/test tasks add the switch themselves |
| Kotlin | 2.4.21 | **mods only**: the mod toolchain compiles a Kotlin mod against this version, and a Kotlin mod needs the `fabric-language-kotlin` language module as the owner of `kotlin.*` at runtime. The game itself needs no Kotlin |

`gradle.properties` sets `org.gradle.java.installations.auto-download=false`, so JDK 25 has to be
installed on the machine already.

## Build, test, run

```bash
./gradlew build                      # compile + the whole test suite (1458 tests, 145 test classes)
./gradlew test                       # tests only
./gradlew test --tests 'cn.enaium.fabricpvz.portable.lawn.BoardTest'
./gradlew run                        # start the game on the real Fabric Loader (game dir: run/)
./gradlew syncDevResources run -PpvzResDir="/path/to/resources"   # copy main.pak/properties into run/, then start
```

`./gradlew run` launches Fabric Loader's own launcher
(`net.fabricmc.loader.impl.launch.knot.KnotClient`) with the working directory set to `run/`, passing
`-Dpvz.gameDir=<project>/run`, `-Dfabric.skipMcProvider=true`, `-Dfabric.development=true` and
`--enable-native-access=ALL-UNNAMED` (plus `-XstartOnFirstThread` on macOS). `run/mods/` is where the
loader reads mods from, exactly as in an installation; `run/.fabric/` is the loader's cache and
`run/userdata/` holds save data. `run` copies nothing into `run/mods/`: the mods a run loads are the
jars you put there yourself, the API's `fabric-api-<version>.jar` among them, so a development run
sees exactly the set a player's installation would. `run` does not depend on the asset sync: to work
from an explicit resource directory, name `syncDevResources` in the same invocation
(`./gradlew syncDevResources run -PpvzResDir=<dir>`), which copies only `main.pak` and
`properties/**` into `run/`. Without it, the game finds its assets through the discovery chain below
(your Steam installation, for instance).

### What the build needs from the other repositories

This project is standalone: every sibling artifact it consumes comes from `mavenLocal()` as a Maven
coordinate, never from a sibling checkout's build directory. The launcher is
`cn.enaium.fabricpvz:fabric-provider` (a `runtimeOnly` dependency, which publishes the real Fabric
Loader with Mixin, ASM and the logging stack); the mod API is an ordinary published coordinate too —
its five feature modules (`api-base`, `api-events`, `api-plant`, `api-resource`, `api-zombie`) are on
the test *compile* class path only, and its aggregate jar (`fabric-api`) is what the loader end-to-end
test finds in a real `mods/` directory (the version is `apiVersion` in `build.gradle.kts`, currently
`1.2.0`). So, from a fresh clone with the siblings checked out side by side:

```bash
# from the fabric-provider repository (publishes the launcher stack: Loader, Mixin, ASM, logging)
./gradlew publishToMavenLocal

# this project, without tests, to put its POM in mavenLocal (the API compiles against this game)
./gradlew publishToMavenLocal -x test

# from the fabric-api repository: publishes the aggregate `fabric-api` jar and the five `api-*` modules
# (its event types are typed against this game's classes; its build resolves the mod Gradle plugin
# `cn.enaium.fabricpvz.loom` from the Gradle Plugin Portal, so the plugin needs neither a checkout nor
# a local publication)
./gradlew publishToMavenLocal

# only the test suite needs this one: the loader end-to-end test loads its jar into a real mods/
# directory
# from the pvz-example-mod repository
./gradlew publishToMavenLocal

# now this project builds and tests in full
./gradlew build
```

After changing the API, publish it again before running this project's tests:

```bash
(cd ../fabric-api && ./gradlew publishToMavenLocal)
cd ../PvZ-Portable && ./gradlew test
```

Compiling the ported game itself needs only the provider; a missing or stale API publication shows up
as an unresolved dependency, or as a test that does not match the API's working tree.

## Game assets (user-provided)

The game looks for the asset root in this order (first hit wins); a directory qualifies when it
contains `main.pak` (case-insensitive) or a `properties/` directory:

1. `-resdir=<path>` command line argument
2. `PVZ_RES_DIR` environment variable
3. the current working directory
4. the directory of the running jar/executable
5. the local **Steam** installation of *Plants vs. Zombies* GOTY (app id 3590), searched through every
   library folder listed in `steamapps/libraryfolders.vdf`
   - macOS: `~/Library/Application Support/Steam/steamapps/common/Plants Vs Zombies/…/Contents/Resources/`
   - Windows: `<steam>/steamapps/common/Plants vs. Zombies/`
   - Linux: `~/.steam/steam`, `~/.steam/root`, `~/.local/share/Steam`, the Flatpak path, `~/.steam/debian-installation`

Writable data (save games, the registry emulation, the resource cache) goes to `-savedir=<path>`,
defaulting to the platform-standard application data directory. Running the game straight from the
project directory therefore creates `userdata/` and `cache64/` next to the sources.

## The distribution (`distZip`)

```bash
./gradlew distZip       # → build/distributions/pvz-portable-1.1.0.zip
./gradlew installDist   # → build/install/pvz-portable/ (the same thing, expanded)
```

The zip is Gradle's own `application`-plugin archive — no hand-rolled packaging — and is **one zip for
every platform**: it carries the LWJGL native libraries of Windows, Linux and macOS on x64 and arm64
(six classifiers × six LWJGL modules = 36 natives jars, about 25 MB of the zip), and the launcher picks
the directory for the machine it runs on.

```
pvz-portable-1.1.0/
├── bin/pvz-portable            start script: Fabric Loader's launcher (KnotClient) — the same entry
├── bin/pvz-portable.bat        point `./gradlew run` uses; the .bat is the Windows counterpart
└── lib/                        pvz-portable-1.1.0.jar and everything needed to start:
                                the launcher stack (Fabric Loader, Mixin, ASM, the Log4j binding and
                                log4j2.xml), slf4j-api, the LWJGL modules and all 36 natives jars
```

The zip contains no `mods/` directory — that is the one thing you add.

**Native libraries (`lib/native/`).** `lib/` also carries the native libraries the game loads itself,
one directory per platform: `lib/native/<os>-<arch>/` holds `libopenmpt.dll` for `windows-x64` and
`windows-arm64` — the Windows builds are the only compiled libopenmpt binaries upstream publishes
(its download page points every other system at its distribution packages) — plus the codec DLLs the
library is linked against (`openmpt-ogg`, `openmpt-mpg123`, `openmpt-vorbis`, `openmpt-zlib`), the
licence as `lib/native/libopenmpt-LICENSE.txt`, and `lib/native/libopenmpt-natives.txt`, the record of
the release archive the files come from with their sha256 sums. The build downloads that archive once,
verifies its sha256, and stages it (`./gradlew libopenmptNatives`): a build without network and
without the cache in the Gradle user home (`caches/pvz/libopenmpt`) fails and says which URL to fetch
instead of producing a zip without them; once cached, `--offline` builds work.

The port looks there **first** and falls back to the system library, so on macOS and Linux it keeps
using the copy your package manager installed (`brew install libopenmpt`, `apt install libopenmpt0`,
…); a `libopenmpt.dylib`/`libopenmpt.so` you build yourself also wins when you drop it into
`<game directory>/lib/native/<os>-<arch>/` (or `<game directory>/native/<os>-<arch>/`). Without any
copy the game plays everything except the two MO3 music files — the sound effects and every `.ogg`
track are decoded in Java — and logs one `Music disabled: libopenmpt is not available` line. When a
copy does load, one INFO line reports the path and the version:
`Music: libopenmpt 0.8.9 loaded from …/lib/native/windows-arm64/libopenmpt.dll`.

**Installing mods.** The zip ships no `mods/` directory and no mod jar, because a mod is something the
player installs. Fabric Loader reads `mods/` from the game directory it is launched on, and for an
unpacked distribution that game directory is the unpacked root — the directory holding `bin/` and
`lib/`, so **`mods/` sits next to `bin/`**:

```bash
unzip -q build/distributions/pvz-portable-1.1.0.zip -d /tmp/pvz
mkdir -p /tmp/pvz/pvz-portable-1.1.0/mods
cp /path/to/fabric-api-1.2.0.jar /path/to/some-mod-1.0.0.jar /tmp/pvz/pvz-portable-1.1.0/mods/
/tmp/pvz/pvz-portable-1.1.0/bin/pvz-portable     # start it from any working directory
```

The start scripts set `-Dpvz.gameDir` to the unpacked root derived from the script's own location
(`$APP_HOME` / `%APP_HOME%`), so `mods/` is found however the script is started — a double-click, a
shortcut, another working directory. It is a **default**: the scripts expand `DEFAULT_JVM_OPTS` before
the user's `JAVA_OPTS`/`PVZ_PORTABLE_OPTS`, and in the JVM a later `-D` of the same name wins, so you
can relocate the game directory:

```bash
PVZ_PORTABLE_OPTS="-Dpvz.gameDir=/some/other/game/dir" /tmp/pvz/pvz-portable-1.1.0/bin/pvz-portable
```

The provider also accepts a `-gamedir <path>` game argument, but resolves `-Dpvz.gameDir` first, so in
an unpacked distribution the script's property shadows it. The scripts already pass
`--enable-native-access=ALL-UNNAMED` and `-Dfabric.skipMcProvider=true` (and `-XstartOnFirstThread` on
macOS); add JVM options through `PVZ_PORTABLE_OPTS` or `JAVA_OPTS`.

Assets are still yours to point at, either as a game argument or through the environment:

```bash
/tmp/pvz/pvz-portable-1.1.0/bin/pvz-portable -resdir "/path/to/Plants vs. Zombies/Resources"
PVZ_RES_DIR="/path/to/resources" /tmp/pvz/pvz-portable-1.1.0/bin/pvz-portable
```

The zip is also what **mod builds compile against**: a mod project's `pvz(...)` configuration names
this game, takes the release asset `pvz-portable-<version>.zip` of that version and puts its `lib/`
on the mod's compile/test/run class path. Changing what goes into `lib/` therefore changes what mods
link against — see the `loom` repository for the toolchain itself.

### On Windows

The Steam installation is found by itself (`steamapps/libraryfolders.vdf` → every library folder), so
the retail `Plants Vs Zombies` directory — including the Steam build's `.au` sounds — needs no wrapper
directory and no converted assets. Verified on Windows 11 ARM64 with a Liberica JDK 25 and the real
Steam assets (docs/VERIFICATION.md §59).

**Rendering backend.** The SDL default renderer can take the JVM down inside `SDL_CreateRenderer`
(`EXCEPTION_ACCESS_VIOLATION`) on some Windows setups — the launcher then has no `RENDERER:` line and
no window content. Ask for the software backend instead:

```bat
set PVZ_PORTABLE_OPTS=-Dpvz.renderer=software
bin\pvz-portable.bat
```

A missing `RENDERER:` line means the requested GPU backend failed and the port fell back to its
offscreen software renderer, so if the picture looks wrong, check the log line first.

**Music.** The MO3 tracks need libopenmpt, and the Windows build of it travels inside the distribution
(`lib/native/windows-x64` / `windows-arm64`, see "The distribution" above), so nothing has to be
installed. The system copy is still the fallback on every platform: `libopenmpt.dll` on `PATH` or in
`C:\Program Files\libopenmpt\`, Homebrew's on macOS, the distribution package on Linux. Without any
copy the log says `Music disabled: libopenmpt is not available` and `music failed to load` twice. Both
are non-fatal: the game runs, only the music is silent.

**Sounds.** WAV, Ogg Vorbis, MO3 and AU (µ-law, the Sun/NeXT format the Steam assets use) are decoded
from their content, not their file name. A sound whose container nothing can decode is logged
(`Skipping sound '…': unrecognized audio container in …`) and skipped, while a sound that is missing
from the pak/properties/data files still fails the loading thread — so a broken manifest is not
hidden by the skip.

### On Linux

Verified on Fedora 44 Server (aarch64, OpenJDK 25) with Wayland and with X11: the window dump, both mod
harnesses, a played level, the Steam asset discovery and the MO3 music all match the macOS numbers
(docs/VERIFICATION.md §62).

**Display.** SDL3 needs a display server, and an SSH login has neither `DISPLAY` nor
`WAYLAND_DISPLAY` set. Both routes work — Wayland through the session's runtime directory:

```bash
XDG_RUNTIME_DIR=/run/user/$(id -u) WAYLAND_DISPLAY=wayland-0 SDL_VIDEODRIVER=wayland bin/pvz-portable
```

and X11/Xwayland, which needs the session's own authorization file: a bare `DISPLAY=:0` fails with
`Authorization required, but no authorization protocol specified` (and then `SDL_Init(SDL_INIT_VIDEO)
failed: x11 not available`), because an SSH environment has no `~/.Xauthority`:

```bash
DISPLAY=:0 XAUTHORITY=/run/user/$(id -u)/.mutter-Xwaylandauth.XXXXXX SDL_VIDEODRIVER=x11 bin/pvz-portable
```

`Failed to load plugin 'libdecor-gtk.so': failed to init` is SDL's Wayland client-decoration plugin
(libdecor + GTK3) missing on a Server install; SDL falls back to server-side or undecorated windows and
the window works normally, so the line is environment, not a defect.

**Rendering backend.** The default (`opengl`) works even on a machine without a GPU — the VM runs it
through Mesa's llvmpipe — and `-Dpvz.renderer=software` works as well. Both draw the same picture; the
software backend used to lose a race with the window manager's first resize, which is fixed and
explained in docs/VERIFICATION.md §62.

**Native library directory.** The directory name is the one `OpenMptLibrary.nativePlatform()` produces:
`linux-x64` and `linux-arm64`. The architecture is normalized, so `aarch64` maps to `arm64` — a
`lib/native/linux-aarch64/` directory is **not** found.

**Music (libopenmpt).** A distribution package is found by itself now (`dnf install libopenmpt` on
Fedora/RHEL installs `/usr/lib64/libopenmpt.so.0`, `apt install libopenmpt0` the multiarch
`/usr/lib/<triplet>/libopenmpt.so.0`), and a copy in the game directory still wins. Without root, the
same library can be unpacked into the distribution's own native directory:

```bash
mkdir -p ~/libopenmpt && cd ~/libopenmpt
curl -sSLO https://dl.fedoraproject.org/pub/fedora/linux/releases/44/Everything/aarch64/os/Packages/l/libopenmpt-0.8.6-1.fc44.aarch64.rpm
rpm2cpio libopenmpt-0.8.6-1.fc44.aarch64.rpm | cpio -idmv        # → usr/lib64/libopenmpt.so.0.5.5
mkdir -p ~/pvz/lib/native/linux-arm64
cp -L usr/lib64/libopenmpt.so.0.5.5 ~/pvz/lib/native/linux-arm64/libopenmpt.so
```

Its dependencies (`libz`, `libmpg123`, `libogg`, `libvorbis`, `libstdc++`) come from the base system.
Verified: `Music: libopenmpt 0.8.6 loaded from …/lib/native/linux-arm64/libopenmpt.so`, and both MO3
tracks decode to the same PCM as macOS and Windows (MAINMUSIC: 8,373,498 frames, 0.9994 non-zero
samples, peak 14462).

### Releases

A release is cut by hand from a tagged commit: this repository has **no CI automation** — no
workflow, nothing that runs on a push — so a release is a deliberate, local act.

```bash
# from the commit to release (on main), tagged with the version the build states: the tag names the
# release and the asset is named after the same version
VERSION="$(./gradlew -q printVersion)"                       # 1.1.0
git tag -a "$VERSION" -m "pvz-portable $VERSION" && git push origin "$VERSION"
test "${VERSION#v}" = "$(git describe --tags --exact-match)" # tag == version, or stop here

# the distribution is the artifact (`./gradlew build` first if the suite has to pass too)
./gradlew distZip                                            # → build/distributions/pvz-portable-$VERSION.zip

# attach it to the tag's release; `--verify-tag` refuses a tag that is not pushed yet, and re-cutting
# an existing release is `gh release upload "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" --clobber`
gh release create "$VERSION" "build/distributions/pvz-portable-$VERSION.zip" \
  --title "pvz-portable $VERSION" --verify-tag
```

The `test` line is the whole of the "the tag and the project version agree" rule: the version lives in
`build.gradle.kts` alone (`./gradlew -q printVersion` prints it) and the asset name is derived from
it, so a release cannot carry a mislabelled zip. The zip itself does not depend on the machine it is
built on, because `lib/` carries the natives of every supported platform. Leave packaging to Gradle's
`distZip`, never hand-roll an archive, and do not add a workflow back (§6 of `AGENTS.md` says why).

The 1.0.0 release was cut that way, from the same `./gradlew distZip` output.

## Mods

A mod for this game is an ordinary Fabric mod: a jar with a `fabric.mod.json` and (optionally) a Mixin
configuration, dropped into the game directory's `mods/`.

```json
{
  "schemaVersion": 1,
  "id": "my-mod",
  "version": "1.1.0",
  "environment": "*",
  "entrypoints": { "main": ["com.example.MyMod"] },
  "mixins": ["mymod.mixins.json"],
  "depends": { "fabricloader": ">=0.19.0", "pvz-portable": "*", "fabric-api": ">=1.0.0" }
}
```

- **Mod ids.** The game is `pvz-portable`; the API is `fabric-api` (its feature modules `api-base`,
  `api-events`, `api-plant`, `api-resource`, `api-zombie` are nested inside that one jar). A mod that wants the API
  declares `"fabric-api": ">=1.0.0"`, and `"pvz-portable": "*"` for the game — the game's version is
  not something a mod has to follow.
- **Entry points** are Fabric's own interfaces (`net.fabricmc.api.ModInitializer`,
  `ClientModInitializer`, `net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint`). The game provider
  invokes `main`/`client` before the game's main class; `preLaunch` is the loader's own. Fabric Loader
  defines no order between mods, so an initializer must not assume another mod's initializer has run.
- **Mixin** is the real Sponge Mixin that ships with Fabric Loader (`@Mixin/@Inject/@Redirect/
  @Overwrite/@Shadow/@Accessor/@Invoker`). The port runs without mappings, so mixin references are used
  verbatim and no refmap is needed.
- **Content** (custom plants and zombies) comes from `api-plant`/`api-zombie`, and a mod's own art
  (its seed-packet icon, a recoloured retail plant) from `api-resource`: registering a definition
  writes a row into this game's capacity-sized definition tables (`SeedType.MAX_SEED_TYPES`,
  `ZombieType.MAX_ZOMBIE_TYPES`; an empty row means "not registered") and the API's Mixins dispatch the
  definition's behaviour into the game's own methods — the game has no registry and no mod concept of
  its own. Both the seed chooser and the Almanac show registered content on a page after the vanilla
  grid (page 0): a registered seed the player has unlocked joins the chooser's later pages, and the
  Almanac's plant page gets the same kind of later page (its zombie page gets one for every defined
  zombie above the retail range), where the entry's card shows the name and the description the
  registration carries.
- **Events** come from the API: `cn.enaium.fabricpvz.api.lawn.LawnEvents` (level start/end, waves,
  zombie spawn/death/hurt, plant placed/removed/hurt, sun collected, planting validation, every tick,
  before/after draw), `cn.enaium.fabricpvz.api.PvzFabricApi` and
  `cn.enaium.fabricpvz.api.mod.ModQuery` (Fabric Loader's mod queries). The API also ships its own
  Fabric-shaped event bus (`cn.enaium.fabricpvz.api.event`) rather than depending on Fabric API's base
  module.
- **Where mods are loaded from.** Always `mods/` under the game directory. A mod that is only on the
  class path is discovered when the loader runs in development mode
  (`-Dfabric.development=true`, which `./gradlew run` passes) — that is how a mod's own `runClient`
  works. The API in particular is a `mods/` jar in every case, never a class path entry: during
  development you copy `fabric-api-<version>.jar` into `run/mods/` yourself — the build installs
  nothing there.

Building a mod is one plugin line (`id("cn.enaium.fabricpvz.loom")` plus its version), published on the
Gradle Plugin Portal by the `loom` repository; the toolchain brings the loader, Mixin, ASM, the API and
the game (as a distribution, see above), plus a `runClient` task that boots the game through the real
loader. A mod may be written in Kotlin as well as Java: the toolchain compiles Kotlin at the same
class-file target as the Java sources, compiles the mod against `kotlin-stdlib` without bundling or
publishing it, and brings the `fabric-language-kotlin` language module — the single owner of the
`kotlin.*` runtime and the provider of the `"kotlin"` value adapter. A Kotlin mod declares that module
in its `fabric.mod.json` and uses a `class` with a no-argument constructor (not an `object`) as its
entry point, unless it names the adapter explicitly.

## Porting status

The port is complete in the sense that every runnable upstream mode, screen and subsystem has been
implemented; one `UnsupportedOperationException` remains in the game code, for the legacy `.dat`
mid-level save format (below).

> Status: `Done` = implemented and tested; `Partial` = the main mechanics are implemented but a few
> branches throw `UnsupportedOperationException` and are annotated in code; `N/A` = the upstream
> concept cannot or need not be mapped onto the Java side (with upstream evidence).
> Verification: the JUnit suite (`./gradlew test`), pixel-level render-backend tests (software and a
> real GPU backend), and real Steam-asset tests that skip themselves when the assets are not installed.

| Module | Status | Verification |
|---|---|---|
| Engine: SDL3 platform layer (window/input/cursor/clipboard/timing) | Done | `SdlRendererTest` + `SdlRendererGpuTest` (including a real Metal run) |
| Engine: render backend (texture/rotate/flip/tint/clip/offscreen/readback/triangle) | Done | 7 pixel-level tests: 6 for the software renderer + 1 for real GPU (Metal) tinting/triangles |
| Engine: Image/MemoryImage/Graphics/font (desc-parser bitmap fonts) | Done | Per-proxy tests + real font asset tests |
| Engine: asset manifest/PAK/compile cache/DataSync/asset discovery (local + Steam) | Done | `ResourceManagerRealAssetsTest` (691/168/20 entries), `PakInterfaceTest`, `SteamLibraryFinderTest` |
| Engine: audio (OGG/MO3/gain/fade) | Done | 62 audio tests, including 169 real OGG files and 2 MO3 (libopenmpt) |
| Engine: Widget/Dialog/app main loop (100 Hz fixed step) | Done | `WidgetTest`/`WidgetManagerTest`/`SexyAppBaseTest` |
| Game: Reanimator/Attachment/Atlas/particles/effects/Foley/Trail | Done | `ReanimatorTest`/`ParticleTest`/`FoleyTest` etc. + real reanim assets |
| Game: Board/grid/waves/sun/mowers/draw layer order | Done | All 22 `BoardTest` tests green (adjudicated item by item against upstream); `GameBootSmokeTest`: boots with real Steam assets → Adventure 1-1 → 600 ticks without exceptions |
| Game: plants/zombies/projectiles/coins/grid items (complete definition tables) | Done | `PlantTest` (53-row table)/`ZombieTest` (33-row table)/`ProjectileTest`/`CoinTest` |
| Game: LawnApp/screens and dialogs/cutscenes | Done | `LawnAppTest`/per-screen tests/`CutSceneTest` |
| Game: saves (users.dat/userN.dat/mid-level save .v4) | Done | `ProfileMgrTest` byte-level golden samples |
| Game: Zen Garden (garden grid/pots/needs/Stinky/store/tutorial/leave) | Done | 34 `ZenGardenTest` tests (pixel↔grid, need state machine, growth and coins, Stinky state machine, sell-price table); `ChallengeModeCoverageTest` actually runs garden mode and asserts that `mZenGarden.mBoard` has taken over |
| Game: Zombatar (editor/accessory table/palette/TOS/write back to profile) | Done | `ZombatarTest` 12 + `ZombatarWidgetTest` 10 + `ZombatarHeadTest` 3; 4 `GameSelectorTest` tests verifying the button→editor/TOS bindings |
| Game: all challenge mode implementations (Beghouled·Twist / Scary Potter·vasebreaker / I,Zombie / Portal Combat / Slot Machine / Whack-a-Zombie / Zombiquarium / Squirrel / Tree of Wisdom / weather and Last Stand, etc.) | Done | 10 `ChallengeXxx` helpers with 193 tests in total; `ChallengeModeCoverageTest` starts each of the **73 modes**, runs 150 ticks and asserts their distinctive structures (pots, 4 portals, ≥7 squirrels, fish, tombstones, the slot-machine handle, Tree of Wisdom clouds, zombies' preset plants, …) |
| Game: Crazy Dave store upsell cutscene (GAMEMODE_UPSELL) | Done | 12 `CutSceneTest` tests: the full 3300→3317 sequence (each stage desk, store/menu button handoff, `StoreScreen.getItemCost`) |
| Game: Cheat dialog and level jump | Done | `CheatDialogTest` 18 + `LawnAppCheatTest` 8 (including the `sscanf` syntax family and the invalid-input branches) |
| Game: mid-level saves (`.v4`) | Done | 19 `SaveGameTest` tests: in-progress level round-trip, byte-identical rewrite after reading, CRC/version rejection |
| Game: legacy `.dat` mid-level saves | N/A | Upstream README: `.dat` is "raw memory dumps … Do not share this file across platforms"; the port recognizes them by the upstream criterion and gives an actionable hint (open with the upstream version to auto-migrate to `.v4`) without faking a decoder |
| Fabric Loader integration (game provider + API + a real `mods/` directory) | Done | `FabricLoaderEndToEndTest` launches Fabric Loader 0.19.5 in a second JVM with the API jar, the example mod and a fixture mod as the only three jars of a real `mods/` directory — the API is on the class path in neither mode the test runs — and verifies the resolved mod list (game, loader, the API's five modules nested in its aggregate jar, all three mod jars), a mod Mixin applied to game classes, the API's hook chain reaching a mod listener, the fixture's plant and zombie registrations on a real board, and the game booting Adventure level 1 to `SCENE_PLAYING` (300 ticks with the mod's board Mixin running) |
| Distribution / release (the `application` plugin's zip) | Done | `./gradlew distZip` → `pvz-portable-1.1.0.zip` (`bin/` + `lib/` only, ~28 MB) with the 36 LWJGL natives jars, the bundled `lib/native/windows-{x64,arm64}/` libopenmpt (so Windows needs no installed library) and no `mods/` directory; verified outside the repository: unpacked in `/tmp`, `mods/` created next to `bin/`, mod jars copied in, `./bin/pvz-portable` started from an unrelated working directory → Fabric Loader resolves the mods and the game reaches `SCENE_PLAYING` |

## Differences from upstream (deviation log)

| Item | Upstream | This port | Reason |
|---|---|---|---|
| Window/rendering | SDL2 + SDL_GL (GLES2/GL2.1) + custom GL batching | **SDL3's `SDL_Renderer`** (Metal on macOS, software rendering when there is no GPU) | LWJGL only provides SDL3 bindings; it keeps 2D semantics consistent at the lowest code volume and risk |
| Audio | SDL-Mixer-X fork + libopenmpt | **OpenAL + stb_vorbis**; MO3 via **libopenmpt** (JDK FFM) | no JVM version of SDL_mixer; the gain/fade semantics are kept |
| Image decoding | libpng/libjpeg/built-in gif/TGA | **Java ImageIO** (PNG/JPEG/GIF) + self-implemented TGA | removes the native dependency |
| Registry persistence | regemu (`mRegKey` is empty under real assets, so it has no effect) | same (the RegEmu implementation is retained) | consistent with upstream behaviour |
| Compile cache | `cache64/` binary cache | retained, reading and writing the same cache format | see the asset/resource research notes |
| Mixin | Sponge Mixin 0.8 | **the Sponge Mixin build that ships with the real Fabric Loader**, configured by `fabric.mod.json` `mixins` entries | no bespoke engine to maintain; mods use the standard Mixin API and config format |
| Seed chooser | one grid of the retail seeds | **page 0 is that grid**; seed types the player has unlocked beyond the retail range (id ≥ `SeedType.NUM_SEEDS_IN_CHOOSER`, i.e. content a mod registered) get further pages, up to `SeedChooserScreen.CHOOSER_PAGE_SIZE` (48) per page in id order, with prev/next arrows beside "Let's Rock" | mod content needs somewhere to go in the chooser without disturbing the retail layout; with one page nothing beyond the grid is drawn or clickable, so the unmodded chooser is exactly upstream's |
| Almanac | one fixed plant grid and one fixed zombie grid | **page 0 is each of those grids**; the plant page gets further pages for registered seed types the player has unlocked (id ≥ `SeedType.NUM_SEEDS_IN_CHOOSER`, `AlmanacDialog.ALMANAC_PLANT_PAGE_SIZE` = 48 per page in id order) and the zombie page gets them for defined zombie types above the retail range (id ≥ `ZombieType.NUM_ZOMBIE_TYPES`, 25 per page), with prev/next arrows and a `page/total` counter in the free stretch of the bottom button row | same rule as the chooser, and the game side stays neutral: names and descriptions go through the almanac's own `AlmanacDialog.getPlantDescriptionText` / `getZombieNameText` / `getZombieDescriptionText` seams, which `api-plant`/`api-zombie` inject for registered content; a single page draws no arrows and no counter, so the unmodded almanac is exactly upstream's |
| Cached plant frame | `ReanimatorCache::MakeCachedPlantFrame` draws the separate `anim_head_idle` layer for a written-out list of seed types | the rule is read from the plant's reanimation (`anim_head_idle` exists ⇒ draw that layer), which is what the list stood for | the frame is what the cursor preview, the seed packet art and the almanac picture show, and the retail list left a plant registered with the same reanimation headless in all three; the retail set is unchanged (only the five pea shooters' reanimations carry that layer) |
| Window and presentation | window created with `SDL_WINDOW_OPENGL \| SDL_WINDOW_RESIZABLE` (no high-DPI flag); every primitive is drawn straight into the window and scaled by SDL's logical presentation, and the window server stretches an 800x600 frame on a Retina display | the window requests `SDL_WINDOW_HIGH_PIXEL_DENSITY`; a frame is drawn 1:1 into an 800x600 offscreen **canvas** and the renderer composites that canvas into the drawable once per frame (upstream's `mScreenImage` architecture), filtering whole-number magnifications nearest and everything else linearly | the user asked for the SDL window's high-pixel mode; scaling every primitive distorts rotated/skewed quads and, at a fractional scale, copies some source pixels twice and others not at all (uneven text strokes) — see `docs/VERIFICATION.md` §52. A deliberate deviation from upstream, not a porting shortcut |

## Development tasks

The suite is JUnit 6.1.3 (`useJUnitPlatform()`), 1458 tests in 145 test classes. Every test gets a
180-second wall-clock budget in `SEPARATE_THREAD` mode, so a stuck loop fails one test instead of
hanging the build; the test JVM runs headless (`SDL_VIDEODRIVER=dummy` with the software renderer) and
adds `-XstartOnFirstThread` on macOS. Tests that need real assets take them through
`pvzp/TestAssets.java` (`-Dpvz.test.resources=<dir>` or the standard discovery chain) and are gated
with `Assumptions`, so the suite stays green without the game installed.

Fixtures worth knowing when you write tests: `test/HeadlessTestApp.java` (fills the asset tables with
stub images/fonts and a stub sound manager, so no real assets are needed after `new LawnApp()`),
`lawn/LawnEntityTestSupport.java`, `pvzp/reanim/ReanimTestSupport.java`,
`pvzp/audio/TestAudio.java` (builds WAVs in memory) and `contentmodfixture/` (the loader end-to-end
fixture).

These tasks run the real thing by hand (they are not part of `test`); all of them take their switches
as Gradle properties (`-P…`):

| Task | Purpose |
|---|---|
| `./gradlew dumpWindow` | opens a real window and writes both images of every frame (0.5 s apart) to `build/window-dump/`: `frame-NNN.png` is the 800x600 canvas the game drew into, `window-NNN.png` what the window shows (the canvas composited into the drawable); each frame prints `appLogical=`/`canvas=`/`window=`/`present=`; `-Ppvz.renderer=software\|metal\|opengl\|…` selects the backend, `-Ppvz.window.dumpDir=…` the output directory |
| `./gradlew sceneDump` | frames of one rendering scenario, for looking at a specific animation frame by frame; `-Ppvz.dump.scenario=graveRise\|fume\|packet\|zomboni` |
| `./gradlew zoomDump` | the frames around the level-award seed packet being picked up (scaled up to 2×), plus a probe for the zoomed drawing's filtering; `-Ppvz.zoom.dumpDir/resDir/logClicks` |
| `./gradlew playthrough` | plays Adventure mode end to end from the menu using **synthetic SDL events** through the real input path; `-Ppvz.play.firstLevel/lastLevel/firstMode/lastMode/tickBudget/fast/fromMenu/logClicks` |
| `./gradlew bench` | fps / draw time / GC of the real loop; `-Ppvz.bench.seconds/level/zombies/cpuFrames/iterations/jfr` |

Three hard rules when writing such a driver (they are also documented in the drivers themselves):

1. read the window contents back **before** `SDL_RenderPresent` (after present the back buffer is
   undefined, and on Metal the read-back is blank);
2. do the read-back on the frame-loop thread (the SDL renderer is not thread-safe; reading it from
   another thread aborts on Metal);
3. the "cheats" a driver uses (instakilling zombies, compressing the wave countdown) must live in the
   driver's own hook listeners and **must not touch game code**.

Two more things worth knowing before changing rendering code:

- **Logical vs drawable size, and the canvas.** The port distinguishes the logical window size (used for app
  layout, e.g. 800×600) from the drawable size (used for rendering, 2940×1790 pixels for a 1470×895-point
  window on a Retina display). The `WINDOW_RESIZE` event carries only the logical size; feeding the drawable
  size back in as the window size stretches the canvas and the window appears to show nothing. Rendering goes
  the other way: the framework draws every frame 1:1 into an 800×600 **canvas** (an offscreen texture created
  only when the drawable differs from the canvas) and `RendererBackend.compositeToOutput()`, called at the end
  of `WidgetManager.drawScreen`, scales it into the drawable once (`SdlRenderer`). `renderOutputWidth/Height`
  is the canvas, `presentedWidth/Height` the drawable, `readTexturePixels(0, ..)` reads the canvas and
  `readPresentedPixels(..)` the window, and `logicalPresentationRect()` is in window **points** (mouse space).
  On a window whose drawable equals the canvas none of this happens and the window is drawn into directly.
- **Provenance markers.** Every ported type/method records its upstream origin in the javadoc —
  `Ported from src/Lawn/Board.cpp:246 (BoardInitForPlayer)` — with the `src/...:<line>` kept in step
  with the upstream source (355 of 375 files in `src/main`, about 2358 markers in total). Deviations
  are marked `PORT-NOTE …` next to the code that deviates. These comments are the evidence chain for
  "why does it look like this" and are expected to be kept accurate.

## Licence

- The port is **LGPL-3.0-or-later**, following the upstream project it is ported from
  ([wszqkzqk/PvZ-Portable](https://github.com/wszqkzqk/PvZ-Portable), © 2026 Zhou Qiankang), with the
  `Ported from …` provenance retained in the file headers. The licence texts and notices ship in this
  repository, unchanged: `LICENSE` and `COPYING` (LGPL-3.0 + GPL-3.0), `NOTICE`, and
  `SexyAppFramework-LICENSE.txt` (the SexyAppFramework-related parts follow the PopCap Games Framework
  licence).
- **No game assets are shipped**: *Plants vs. Zombies* is copyright PopCap/EA, and this project is not
  affiliated with either. Runtime assets are supplied by the user (see
  [Game assets](#game-assets-user-provided)).
- Third-party dependencies: LWJGL (BSD-3), Fabric Loader (Apache-2.0), Sponge Mixin (MIT), ASM (BSD-3),
  Gson (Apache-2.0), Kotlin (Apache-2.0 — the mod toolchain's Kotlin plugin, and the standard library
  that the `fabric-language-kotlin` mod provides to Kotlin mods), JUnit (EPL-2.0, tests only), and
  **libopenmpt 0.8.9 (BSD-3-Clause)** — the optional MO3 decoder, whose Windows build the distribution
  ships in `lib/native/` with upstream's `LICENSE.txt` as `lib/native/libopenmpt-LICENSE.txt`. The four
  codec DLLs next to it are upstream's own builds of, and carry the licences of, libmpg123 (LGPL-2.1),
  libogg and libvorbis (BSD-3) and zlib; upstream lists those texts in the `Licenses/` directory of its
  binary distribution (`lib.openmpt.org/files/libopenmpt/bin/`), which is not the archive this build
  pins — the file-by-file provenance is in `lib/native/libopenmpt-natives.txt`.

## Related projects

The game is one repository of the **FabricPvZ** family. The others are dependencies of this one, not
part of its build:

| Repository | What it is, and how this project relates to it |
|---|---|
| `fabric-provider` | the Fabric Loader game provider: finds the game (`GameProvider`), unlocks the class-loading path so Mixin can rewrite game classes, runs mod entry points, then calls the game's main class. It publishes the launch libraries — the real Fabric Loader, Mixin, ASM, and the SLF4J + Log4j stack — which is why this project depends on it as its single `runtimeOnly` launcher dependency and why `lib/` of the distribution is what it is |
| `fabric-api` | the mod API: `api-base` (event bus + API entrypoints), `api-events` (lawn and lifecycle events), `api-plant` and `api-zombie` (content registration, plus the Mixins that dispatch a registered definition's behaviour into this game's methods), `api-resource` (a mod's own images: slots the game never draws, the game's own packet/cached-frame painters, per-track reanimation overrides, with its own entrypoint and Mixin). Its event types and Mixins are typed against this game's classes, and it is installed into `mods/` as the one aggregate mod jar `fabric-api` — never a class path dependency |
| `loom` | the mod Gradle plugin `cn.enaium.fabricpvz.loom`, published on the Gradle Plugin Portal: one plugin line gives a mod the loader, Mixin, ASM, the API and **this game** as a distribution, plus a `runClient` task that boots the game through the real loader |
| `pvz-example-mod` | reference mod: API events, mod hooks and a Mixin into the game. This project's loader end-to-end test loads its jar from `mavenLocal` |
| `speed-mod` | game-speed controls (0.5×–3× and pause) as buttons next to the menu button |
| `auto-collect-mod` | collects sun and coins for the player, with a toggle button |

Mods declare `"pvz-portable": "*"` in their `fabric.mod.json`, so a new version of the game does not
require mod changes.
