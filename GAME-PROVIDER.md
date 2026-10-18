# The game provider

This is the bridge between the PvZ-Portable Java port and the **real [Fabric Loader](https://github.com/FabricMC/fabric-loader)**:
one `net.fabricmc.loader.impl.game.GameProvider` implementation, registered through
`META-INF/services/net.fabricmc.loader.impl.game.GameProvider`, plus the launch libraries the loader needs
(`net.fabricmc:fabric-loader` 0.19.5 with its Mixin and ASM, exactly the library set of the loader's own
manifest `fabric-loader-0.19.5.json`, plus the **logging stack**: `org.slf4j:slf4j-api` with
`org.apache.logging.log4j:log4j-core`, its SLF4J binding and this module's `log4j2.xml`).

The Kotlin runtime is *not* one of them: it is `fabric-language-kotlin`'s (the mod a Kotlin mod declares in
`fabric.mod.json` and installs — its nested `kotlin-stdlib` is the one copy a whole installation resolves
`kotlin.*` from). See `loom/README.md`, "Kotlin".

Everything else — mod discovery, `fabric.mod.json` parsing, dependency/version resolution, the mod class
loader, the Mixin environment, language adapters, entry point instantiation — is Fabric Loader's, not ours.
The game itself (and this project's own sources) stays free of any Fabric dependency: this module knows the
game only by class name.

## Logging

Fabric Loader's own log is `net.fabricmc.loader.impl.util.log.Log` plus a `LogHandler`, and the game provider
is the one who chooses that handler: Minecraft's provider installs the loader's Log4j or SLF4J bridge at the
end of its `initialize`, and this provider installs the **SLF4J** bridge
(`net.fabricmc.loader.impl.game.minecraft.Slf4jLogHandler`) in its *constructor* — one step earlier, on
purpose: `Knot#init` instantiates the provider and only then flushes the loader's buffered first lines, so a
handler installed any later would leave the beginning of a boot log in the built-in console format. The
constructor is therefore the last moment at which the loader's very first line can still be routed, and
`Log#init` replays everything logged before it into the new handler.

Because the Mixin subsystem logs through the same `Log` (Knot's Mixin service implements Mixin's logger as a
`Log` category: `[FabricLoader/Mixin]`) and every mod logs through `org.slf4j.LoggerFactory`, the loader, Mixin
and the mods end up in one Log4j context, one configuration (`log4j2.xml`, a resource of this module, so every
launch path reads it off the class path) and one line format:

```
[HH:mm:ss] [thread/LEVEL] [logger]: message
```

The logger of a loader or Mixin line is its `Log` category (`FabricLoader`, `FabricLoader/Mixin`, …), the
logger of a mod line is the mod id it created its logger with. The root level comes from Fabric Loader's own
switch, `-Dfabric.log.level=<trace|debug|info|warn|error>` (INFO without it), so the knob a loader user knows
still works.

If the stack is not on the class path (a consumer that excluded it), the provider keeps the loader's built-in
console handler and logs one warning about it — a boot without the workspace's log configuration still shows
its loader lines.

## Launching the port

```bash
java -cp <game (provider included) + loader + Mixin + ASM + logging + api modules + mods/*.jar> \
     net.fabricmc.loader.impl.launch.knot.KnotClient
```

`net.fabricmc.loader.impl.launch.knot.KnotClient` is the Fabric Loader's client launcher (its server
counterpart is `KnotServer`; this port is a desktop game and ships no dedicated server). The loader finds
the game through `cn.enaium.fabricpvz.provider.PvzGameProvider`, reports it to mods as the built-in mod
`pvz-portable`, loads `mods/` (or `-Dfabric.modsFolder=<dir>`), runs the mod entry points and only then calls
the game's `main(String[])`.

`PvZ-Portable`'s `./gradlew run` and its loader integration test do exactly this; run
`./gradlew run` there with a mod jar in `PvZ-Portable/run/mods/` to try it.

## What the provider implements

| GameProvider member | Behaviour |
|---|---|
| `getGameId` / `getGameName` / `getRawGameVersion` / `getNormalizedGameVersion` | `pvz-portable` / `PvZ Portable` / `1.0.0` (the game's own version, see `PvZ-Portable`'s `version`; override with `-Dpvz.gameVersion`) |
| `getBuiltinMods` | One built-in mod (`pvz-portable`) built with `BuiltinModMetadata`, so the game needs no `fabric.mod.json` in its jar |
| `locateGame` | Parses the game arguments, then finds the class path entry (directory or jar) containing `cn.enaium.fabricpvz.portable.lawn.Main`; returns `false` when the game is absent, so the loader can try another provider |
| `getEntrypoint` | `cn.enaium.fabricpvz.portable.lawn.Main` (override with `-Dpvz.game.mainClass`) |
| `getLaunchDirectory` | `-Dpvz.gameDir`, else the `-gamedir <path>` argument, else the working directory; loaded mods come from its `mods/` (or `-Dfabric.modsFolder`) |
| `initialize` | Sets up the (patch-free) `GameTransformer` |
| `getBuiltinTransforms` | Game classes: `WIDEN_ALL_PACKAGE_ACCESS` + `CLASS_TWEAKS`; everything else: `CLASS_TWEAKS`. `STRIP_ENVIRONMENT` is deliberately not requested: the port uses no `net.fabricmc.api.Environment` annotations, so stripping has nothing to do |
| `unlockClassPath` | Adds the game (and the `-Dpvz.extraClasspath` entries) to the Knot class loader — this is what makes Mixin able to transform game classes |
| `launch` | Runs the mod `main` entry points (`ModInitializer`), then the `client` entry points (`ClientModInitializer`) when the loader reports the client environment, then invokes the game entry point through the loader's class loader. Fabric Loader invokes `preLaunch` itself, but `main`/`client` are the game's job (Minecraft does it from its patched entry point) |

The provider does not restrict the parent class path (`setValidParentClassPath`), so LWJGL, the JDK and the
mod loader's own libraries keep coming from the application class loader while the game, the API and the mods
are defined by the loader — with Mixin applied.

## Properties

| Property | Meaning |
|---|---|
| `-Dpvz.gameDir=<dir>` | Game directory (where `mods/` and the `.fabric/` cache live); `-gamedir <dir>` on the command line works too |
| `-Dpvz.gameVersion=<version>` | Version reported for the built-in game mod (default `1.0.0`, the game's version) |
| `-Dpvz.game.mainClass=<class>` | Replaces the game entry point (used by this port's loader integration test to boot a fixture) |
| `-Dpvz.extraClasspath=<paths>` | Extra class path entries for the game class loader (path separator separated); the loader integration test uses it for its fixtures |
| `-Dfabric.modsFolder=<dir>` | Fabric Loader: the mods directory |
| `-Dfabric.development=true` | Fabric Loader: also discover mods from the class path, like a Loom development run |
| `-Dfabric.skipMcProvider=true` | Skip Fabric Loader's Minecraft detection pass (this provider is the only one that can be present) |
| `-Dfabric.noGui=true` | Never open Fabric Loader's Swing error window (headless runs) |

## Build

Part of `PvZ-Portable` (`src/main/java/cn/enaium/fabricpvz/provider/`), so it is built - and tested - with
the game:

```bash
./gradlew build     # compile + the test suite, the provider's own tests included
```

The launch libraries (Fabric Loader, Mixin, ASM, the logging stack) are this project's `runtimeOnly`
dependencies, so `lib/` of the distribution and the version package's library list both carry them.
A mod that does not use Kotlin is unaffected by an installation's `fabric-language-kotlin`; a Kotlin mod
compiles against the stdlib (`compileOnly`, wired by `loom`) and resolves `kotlin.*` from the language
module's nested jar at runtime.

## License

MIT (see `LICENSE`). The game port itself (`PvZ-Portable`) is LGPL-3.0-or-later with the PopCap framework licence.
