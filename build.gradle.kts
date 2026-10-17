import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.zip.ZipFile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    id("java-library")
    application
    `maven-publish`
}

group = "cn.enaium.fabricpvz"
// The game's version: 1.1.0, the version every artifact of this workspace carries (the loader, the
// API, the toolchain and the mods carry the same one - it is the workspace's release number, independent
// of the upstream C++ project's tags). It is also the version a release reads (see the `printVersion` task)
// and the name of the distribution zip (`pvz-portable-<version>.zip`).
version = "1.1.0"

// The artifact's own name, matching the Maven artifactId (`cn.enaium.fabricpvz:pvz-portable`), the
// distribution and the release asset: `build/libs/pvz-portable-1.1.0.jar`, `lib/pvz-portable-1.1.0.jar`
// inside the zip, `~/.m2/repository/cn/enaium/fabricpvz/pvz-portable/1.1.0/pvz-portable-1.1.0.jar`.
base {
    archivesName = "pvz-portable"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val lwjglVersion = libs.versions.lwjgl.get()

fun lwjglNatives(module: String, classifier: String) = "org.lwjgl:$module:$lwjglVersion:$classifier"

// The platforms the distribution ships native libraries for: Windows, Linux and macOS on x64 and
// arm64. The list is written out rather than derived from the build machine, because the release zip
// has to be the same zip wherever it is built: these are LWJGL's published natives classifiers, and
// they are the same six for every LWJGL module this game uses (`lwjgl`, `lwjgl-sdl`, `lwjgl-opengl`,
// `lwjgl-openal`, `lwjgl-stb`, `lwjgl-nfd` all publish `natives-windows`, `-windows-arm64`,
// `-linux`, `-linux-arm64`, `-macos` and `-macos-arm64`), so no platform of the set is a
// combination LWJGL has no artifact for.
//
// All six sit on the one launch class path because LWJGL 3.4 lays its natives out per platform
// inside the jar (`linux/arm64/org/lwjgl/liblwjgl.so`, `macos/arm64/org/lwjgl/liblwjgl.dylib`, ...):
// the loader looks up the directory of the machine the game runs on and never sees the other five,
// which is why the zip can carry all of them instead of one.
val distributionNativePlatforms = listOf(
    "natives-windows", "natives-windows-arm64",
    "natives-linux", "natives-linux-arm64",
    "natives-macos", "natives-macos-arm64",
)

val lwjglArtifacts = listOf("lwjgl", "lwjgl-sdl", "lwjgl-opengl", "lwjgl-openal", "lwjgl-stb", "lwjgl-nfd")

// ------------------------------------------------------------------------------------------------
// libopenmpt: the optional native MO3 decoder, shipped inside the distribution.
//
// The retail music is the only module audio the game has - sounds/MAINMUSIC.MO3 and
// sounds/MAINMUSIC_HIHATS.MO3 of main.pak; every other piece of music and every sound effect is Ogg
// Vorbis or AU and decoded in Java - and only libopenmpt decodes MO3. It stays an *optional*
// dependency: the port probes the game directory's own native directory first and falls back to the
// system library (Homebrew, apt, ...), so a machine without it keeps playing everything except the
// MO3 music (see `OpenMptLibrary`).
//
// Upstream publishes compiled binaries for Windows only: lib.openmpt.org/libopenmpt/download lists
// Windows arm64/amd64/x86 builds and, for every other operating system, "distribution packages".
// This build therefore ships the two Windows builds and macOS/Linux keep the system lookup. The
// archive below is the official development-libs package of the pinned release: it holds the
// per-architecture DLLs (with the import libraries and headers, which the distribution does not
// carry) and the OpenMPT licence.
//
// The five DLLs of a platform are one unit: libopenmpt.dll *statically* imports openmpt-mpg123.dll,
// openmpt-vorbis.dll and openmpt-zlib.dll, and openmpt-vorbis.dll imports openmpt-ogg.dll. They stay
// in the one directory of the platform - the port loads the satellites from there by absolute path
// before loading libopenmpt.dll itself, which is what makes the packaged copy work on a machine
// where nothing else puts that directory on the DLL search path.
//
// No binary is committed to git: the build downloads the pinned archive once into the Gradle user
// home, verifies the archive's sha256 and the sha256 of every member it extracts, and stages what
// the distribution and the development run carry. A build without network and without the cached
// archive fails with the URL instead of silently producing a release zip without the libraries;
// with the archive cached, `--offline` builds work.
val libopenmptVersion = "0.8.9"
val libopenmptArchiveName = "libopenmpt-$libopenmptVersion+release.dev.windows.vs2022.zip"
val libopenmptArchiveUrl = "https://lib.openmpt.org/files/libopenmpt/dev/$libopenmptArchiveName"

/** The sha256 of [libopenmptArchiveUrl]: the identity of the one download this build makes. */
val libopenmptArchiveSha256 = "a23e375e576e3a6997ffeeb6cf34488e9020e2a5d8226856b02628b6a87c5e8e"

/** The directory inside the archive each platform's DLLs come from. */
val libopenmptArchiveDirectories = mapOf("windows-x64" to "bin/amd64", "windows-arm64" to "bin/arm64")

/**
 * What each platform directory of `lib/native/` carries, in load order (dependencies first), with
 * the sha256 the extracted file has in the pinned archive. The archive's sha256 above pins the
 * download; these pin what the build puts in the zip, and the distribution test asserts the zip's
 * entries against them.
 */
val libopenmptLibraries = linkedMapOf(
    "windows-x64" to linkedMapOf(
        "libopenmpt.dll" to "9fa3131204f44a3b157f22f9f0ae11c5cccb5befa37e91a2476567aa0f4b0d94",
        "openmpt-ogg.dll" to "bbeea26588404a00ae964f1c4aceb6d6474e5885f8337491a4c69e29e8516eb6",
        "openmpt-mpg123.dll" to "f15edd24576d88fa08a153c4a7f9d9d7956449e4fc67758d660ad35d9e1696df",
        "openmpt-vorbis.dll" to "7393d017e2930d57c12c016de21c6fa658f862481758ba394823f6ac5e013df2",
        "openmpt-zlib.dll" to "966d96e9aaf5cbd9e1b600435af1180a3a643ace17a0e782a5becf378b25445b",
    ),
    "windows-arm64" to linkedMapOf(
        "libopenmpt.dll" to "7449ebd96057b190c05faa9e38ca603c996c80447ce33052c0aad656bcfd7589",
        "openmpt-ogg.dll" to "d12f41638db1fe6db35b75fa712c7e2ea3b247a3281e8bb85ecc3ffde0415fb4",
        "openmpt-mpg123.dll" to "d1d389cbc3222e308e7eba17f27920367a4a7aceb8504c9322bbd052ddb53d87",
        "openmpt-vorbis.dll" to "9e6c8c6fa4008fce530902c94cf3ea16d2d62fe0c059cd9444544a4c4f77d3a5",
        "openmpt-zlib.dll" to "3f198cd630b6ec9b25310de5b5d407e2021d7d8a617beb5252cd1c1875f2b4b0",
    ),
)

/** The sha256 of the archive's own `LICENSE.txt`, the licence the distribution carries. */
val libopenmptLicenceSha256 = "2c999e99533c10a347916a72e075bacf10fcedeff793bd90d41d812b46161da2"

/** The sha256 of a file, as the identity of what was downloaded and of what was extracted. */
fun sha256Of(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}

/**
 * Makes sure the pinned archive is in [target] and returns whether it had to be downloaded. The
 * download lands as a temporary file next to it and is moved into place only once it is complete,
 * so an interrupted download never leaves a truncated archive behind (the same shape as the
 * toolchain's own distribution download, `../loom`).
 */
fun fetchLibopenmptArchive(url: String, target: Path, offline: Boolean): Boolean {
    if (Files.isRegularFile(target)) {
        return false
    }
    if (offline) {
        throw GradleException(
            "libopenmpt $libopenmptVersion is not cached ($target) and Gradle runs with --offline, so" +
                " the official release binaries cannot be downloaded. Run once without --offline (the" +
                " archive is then cached for every later build), or download $url into that file by hand.",
        )
    }

    Files.createDirectories(target.parent)
    // Per-process temporary file: two builds of the workspace may fetch the same archive at once.
    val temporary = target.resolveSibling(target.fileName.toString() + ".part-" + ProcessHandle.current().pid())
    Files.deleteIfExists(temporary)
    try {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "pvz-portable/" + project.version)
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().use { it.readNBytes(256) }
            throw GradleException("HTTP ${response.statusCode()}")
        }
        response.body().use { body -> Files.newOutputStream(temporary).use { body.copyTo(it) } }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
    } catch (exception: Exception) {
        Files.deleteIfExists(temporary)
        throw GradleException(
            "Cannot download $url into $target: ${exception.message}. The native libopenmpt" +
                " libraries are part of the distribution, so this build fails instead of producing a" +
                " release zip without them.",
            exception,
        )
    }
    return true
}

// The staged `lib/native/` of the distribution and of the development run. Its layout is the
// distribution's: the platform directories, the licence and a manifest naming the archive every
// file comes from.
val libopenmptNativeStage = layout.buildDirectory.dir("libopenmpt-native")

val libopenmptNatives = tasks.register("libopenmptNatives") {
    group = "distribution"
    description = "Downloads the pinned official libopenmpt binaries (sha256-verified) and stages lib/native/."
    val archive = gradle.gradleUserHomeDir.resolve("caches/pvz/libopenmpt").resolve(libopenmptArchiveName)
    // The archive in the Gradle user home is an implicit input; the two properties below are the
    // version and the hash, i.e. what changes when the pin changes.
    inputs.property("libopenmptVersion", libopenmptVersion)
    inputs.property("libopenmptArchiveSha256", libopenmptArchiveSha256)
    outputs.dir(libopenmptNativeStage)

    doLast {
        val target = libopenmptNativeStage.get().asFile
        if (fetchLibopenmptArchive(libopenmptArchiveUrl, archive.toPath(), gradle.startParameter.isOffline)) {
            logger.lifecycle(
                "libopenmpt {}: downloaded {} from {}", libopenmptVersion, libopenmptArchiveName, libopenmptArchiveUrl,
            )
        }

        val archiveSha256 = sha256Of(archive.toPath())
        if (!archiveSha256.equals(libopenmptArchiveSha256, ignoreCase = true)) {
            // A wrong hash means the cached file is not the archive this build pinned (an interrupted
            // download that somehow moved into place, a partial copy, or a changed upstream file):
            // drop it and say so, instead of packaging something else under the pinned identity.
            archive.delete()
            throw GradleException(
                "libopenmpt $libopenmptVersion: $archive has sha256 $archiveSha256 instead of the pinned" +
                    " $libopenmptArchiveSha256. The file was deleted; a build with network downloads" +
                    " $libopenmptArchiveUrl again.",
            )
        }

        target.deleteRecursively()
        Files.createDirectories(target.toPath())
        val members = StringBuilder()
        ZipFile(archive).use { zip ->
            for ((platform, libraries) in libopenmptLibraries) {
                val archiveDirectory = libopenmptArchiveDirectories.getValue(platform)
                val platformDirectory = target.resolve(platform)
                Files.createDirectories(platformDirectory.toPath())
                for ((name, expected) in libraries) {
                    val entry = zip.getEntry("$archiveDirectory/$name")
                        ?: throw GradleException(
                            "libopenmpt $libopenmptVersion: $libopenmptArchiveName carries no $archiveDirectory/$name.",
                        )
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(platformDirectory.resolve(name).toPath()).use { input.copyTo(it) }
                    }
                    val sha256 = sha256Of(platformDirectory.resolve(name).toPath())
                    if (!sha256.equals(expected, ignoreCase = true)) {
                        throw GradleException(
                            "libopenmpt $libopenmptVersion: $archiveDirectory/$name was extracted with" +
                                " sha256 $sha256 instead of the pinned $expected.",
                        )
                    }
                    members.append(platform).append(' ').append(name).append(' ').append(sha256).append('\n')
                }
            }

            val licence = zip.getEntry("LICENSE.txt")
                ?: throw GradleException("libopenmpt $libopenmptVersion: $libopenmptArchiveName carries no LICENSE.txt.")
            zip.getInputStream(licence).use { input ->
                Files.newOutputStream(target.resolve("libopenmpt-LICENSE.txt").toPath()).use { input.copyTo(it) }
            }
        }

        val licenceSha256 = sha256Of(target.resolve("libopenmpt-LICENSE.txt").toPath())
        if (!licenceSha256.equals(libopenmptLicenceSha256, ignoreCase = true)) {
            throw GradleException(
                "libopenmpt $libopenmptVersion: the licence was extracted with sha256 $licenceSha256" +
                    " instead of the pinned $libopenmptLicenceSha256.",
            )
        }

        Files.writeString(
            target.resolve("libopenmpt-natives.txt").toPath(),
            "libopenmpt " + libopenmptVersion + "\n" +
                "url=" + libopenmptArchiveUrl + "\n" +
                "archive-sha256=" + libopenmptArchiveSha256 + "\n" +
                "# Windows binaries only: upstream publishes no macOS or Linux binaries (see" +
                " lib.openmpt.org/libopenmpt/download, where every other operating system is" +
                " \"distribution packages\"), so those platforms use the system library.\n" +
                "# platform file sha256\n" +
                members,
        )
        logger.lifecycle(
            "libopenmpt {}: staged {} ({} files, {} platforms)",
            libopenmptVersion,
            target,
            libopenmptLibraries.values.sumOf { it.size } + 2,
            libopenmptLibraries.size,
        )
    }
}

// The mods the loader end-to-end test (FabricLoaderEndToEndTest) finds in a real mods/ directory:
// this workspace's API as one mod and the example mod. Both are resolved from mavenLocal and copied
// into build/testmods by [testModsJar] below. Deliberately NOT on the test classpath: mods load from
// the mods directory exactly like they do in a real installation.
val modsForTest by configurations.creating

// The API release this project's tests are written against, published to mavenLocal like every other
// artifact this build consumes. Changing the API means publishing it again before this project's
// tests can see the change:
//
//     (cd ../fabric-api && ./gradlew publishToMavenLocal)
//
// This project is standalone: the API arrives as a repository coordinate, never as a file of a
// sibling checkout, and this build copies nothing into the game directory's mods/.
val apiVersion = "1.2.0"

// The API is a *mod* of the game directory, and never a dependency of this project.
//
// Fabric parity is why it cannot be one: fabric-api depends on this game in turn (its event types
// are typed against the game's classes), so the two projects cannot depend on each other. It is
// consumed in two shapes, and they are not interchangeable:
//  * the aggregator, `cn.enaium.fabricpvz:fabric-api` (`fabric-api-<version>.jar`), is the whole API
//    as **one mod**: its five feature modules sit inside it as `META-INF/jars/*.jar` and the loader
//    loads them from there. This is the jar a `mods/` directory takes - the loader end-to-end test
//    installs it into build/testmods through `modsForTest` above - and the only API jar a player
//    ever copies into an installation's `mods/`;
//  * the five feature modules (`api-base`, `api-events`, `api-plant`, `api-resource`, `api-zombie`)
//    are what `javac` needs, because a class inside a nested jar is not on a compile class path: the
//    test fixtures - the only sources of this project that import `cn.enaium.fabricpvz.api.*` -
//    compile against the modules themselves (see `testCompileOnly` below), and against nothing else.
val apiModules = listOf("api-base", "api-events", "api-plant", "api-resource", "api-zombie")

dependencies {
    // The game's own output goes through the SLF4J API (see Common.LOGGER_NAME): the ported
    // SexyAppFramework helpers, which every game class already logs through, call an org.slf4j.Logger
    // instead of System.out. Only the API is a dependency of this module - the implementation is
    // deliberately absent, because the provider publishes the workspace's one binding (Log4j, bound
    // through log4j-slf4j2-impl) and its log4j2.xml as launch libraries: adding either here would
    // make a second binding and a second configuration possible. `api` so that the version is the
    // one this build compiles and publishes with, which is also the provider's (2.0.20), i.e. one
    // slf4j-api jar in the launch and one in the published dependency graph.
    api(libs.slf4j.api)

    api(platform(libs.lwjgl.bom))
    lwjglArtifacts.forEach { module ->
        api("org.lwjgl:$module")
        // The natives of every platform above, not the build machine's: `runtimeOnly` is what makes
        // them the launch class path, i.e. what puts them in `lib/` of the distribution *and* in the
        // class path of the `bin/pvz-portable` script (`distZip` ships the runtime class path, and
        // the script is generated from it), so the unpacked zip runs the game on the platform it is
        // unpacked on. The development `run` and the tests use the same class path, i.e. the same
        // jars the zip ships.
        distributionNativePlatforms.forEach { classifier -> runtimeOnly(lwjglNatives(module, classifier)) }
    }

    // The mod loader is the REAL Fabric Loader, provided by the launcher and never a dependency of
    // the game's own code: the application entry point is Fabric Loader's KnotClient, which finds
    // this game through the provider module's GameProvider implementation and loads the game
    // directory's mods/ before invoking the game's main class. The provider artifact publishes the
    // loader, Mixin and ASM as runtime dependencies (exactly the library set of the loader's own
    // manifest), so it is the only launch dependency the game needs.
    runtimeOnly("cn.enaium.fabricpvz:fabric-provider:1.1.0")

    // The game's main sources are Fabric-free; its test fixtures are mods, so they compile against
    // the loader (ModInitializer) and query it (FabricLoader facade) when they run.
    testImplementation(libs.fabric.loader)

    // Fabric parity: fabric-api depends on this game (its events are typed against it), so it is not
    // a dependency of this project in either direction - it is a mod, consumed in the two shapes
    // described above. The fixtures (`src/test/java/.../contentmodfixture/`) are mods in disguise and
    // import the API's public types, so their *compilation* gets the API's five feature modules from
    // mavenLocal - compile-only, and deliberately not `testRuntimeOnly`: nothing of the test class
    // path may carry the API, because the game has to find it exactly where a player's installation
    // keeps it, in the mods directory the loader reads ([testModsJar] below).
    apiModules.forEach { testCompileOnly("cn.enaium.fabricpvz:$it:$apiVersion") }

    // The API as one mod and the example mod: the mods the loader end-to-end test loads from the
    // real mods/ directory [testModsJar] builds below. The aggregate jar is the whole API - its five
    // feature modules are nested inside it and the loader loads them from there - so its POM's
    // compile dependencies on those five modules must not drag five further jars next to it: a
    // `mods/` directory takes the aggregate and nothing else of the API.
    modsForTest("cn.enaium.fabricpvz:fabric-api:$apiVersion") { isTransitive = false }
    modsForTest("cn.enaium.fabricpvz:pvz-example-mod:1.1.0")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val testModsDir = layout.buildDirectory.dir("testmods")

// The mods the loader end-to-end test finds in a real `mods/` directory: the API's aggregate jar and
// the example mod, both resolved from mavenLocal (`modsForTest` above). One `Sync`, because
// `build/testmods` is Gradle's own directory: two syncs into it would prune each other's jars. The
// test reads the directory through the `pvz.test.modsDir` system property the test task sets below.
val testModsJar = tasks.register<Sync>("testModsJar") {
    group = "verification"
    description = "Installs the mods the loader end-to-end test loads into build/testmods."
    from(configurations.named("modsForTest"))
    into(testModsDir)
}

application {
    // Fabric Loader's launcher, exactly as the `run` task uses it: it finds this game through the
    // provider module's GameProvider and loads the game directory's mods/ before calling the game's
    // main class. `run` and the distribution therefore launch the same entry point, in the same
    // order, and the distribution's `bin/pvz-portable` script is just that launch without Gradle.
    mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient"
    // The dist directory (and the script names) inside the zip: `pvz-portable-1.1.0/bin/pvz-portable`.
    applicationName = "pvz-portable"
    applicationDefaultJvmArgs = listOf(
        // LWJGL uses JNI; JDK 25 warns (and future JDKs refuse) unless native access is enabled.
        "--enable-native-access=ALL-UNNAMED",
        // This port is the only game provider that can be present, so skip Fabric Loader's Minecraft
        // detection pass over the class path (the `run` task passes the same switch).
        "-Dfabric.skipMcProvider=true",
    )
    // -XstartOnFirstThread (SDL on macOS needs the JVM's main thread to own the first thread) is not
    // here, although every `run` task below passes it: the zip is one zip for every platform, and
    // that switch is not one of them. It is a *launcher* option macOS's `java` consumes
    // (`ProcessPlatformOption` in libjli) and no other platform knows, so a JVM started anywhere else
    // refuses the launch outright ("Unrecognized option: -XstartOnFirstThread", the JVM's own
    // unknown -X handling) - baking it in would make the released zip unable to start on Linux and
    // Windows. It goes into the generated unix script behind a platform test instead (see the
    // startScripts task below), which is the only place available: the script launches on the
    // machine it runs on, the distribution is built on one machine only.
}

// The packaged native libraries (`lib/native/`) are part of the distribution's content, so `distZip`
// and `installDist` carry them, and unzipping is one step: `lib/` next to `bin/` already holds them.
// They are *not* on the runtime class path - the port loads them from the game directory itself
// (see `OpenMptLibrary`), which is also why they are the same directory in the development run.
distributions {
    getByName("main") {
        contents {
            from(libopenmptNatives) {
                into("lib/native")
            }
        }
    }
}

// The distribution: `bin/` start scripts (from the application plugin, generated from the runtime
// class path) and `lib/` with the game jar and every runtime dependency it needs to start - the
// provider, which publishes the real Fabric Loader with Mixin and ASM, the SLF4J API plus the
// provider's Log4j stack and log4j2.xml, and the LWJGL core modules with the native libraries of
// every platform the game ships for (see `distributionNativePlatforms`). That is the whole zip: it
// carries no `mods/` directory and no mod jar, because a mod is something the player installs.
// Fabric Loader reads `mods/` from the game directory it is launched on, so an installation the
// player assembles themselves - create the directory next to the unpacked zip, drop in the API and
// the mods - is the same installation this zip starts, without the game having to guess which mods
// its user wants; a distribution that shipped its own `mods/` would hand every player a set they
// cannot remove without a fresh download.
//
// Nothing is added to the distributions' contents by hand: `lib/` is the runtime class path (what
// `dependencies` above declares) and adding jars any other way would put them in the zip without
// putting them on the class path of the `bin/pvz-portable` script.

// The release contract: the asset a release attaches is `pvz-portable-<version>.zip`, the application
// plugin's own zip (`distZip`, not a hand-rolled archive), so its layout is the layout above. The
// version is the project version, i.e. the single value a release reads.
tasks.named<Zip>("distZip") {
    archiveFileName = "pvz-portable-${project.version}.zip"
}

// The start scripts' content is named by the application name (`pvz-portable`, see above), but Gradle
// names the script <em>files</em> after the project directory (`PvZ-Portable`): the distribution ships
// `bin/pvz-portable` and `bin/pvz-portable.bat`, like the jar and the release asset.
tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "pvz-portable"
    val scriptsDir = layout.buildDirectory.dir("scripts")
    unixScriptFile.set(scriptsDir.map { it.file("pvz-portable") })
    windowsScriptFile.set(scriptsDir.map { it.file("pvz-portable.bat") })
    // Two things cannot come from `applicationDefaultJvmArgs` and go into the generated scripts'
    // own DEFAULT_JVM_OPTS assignment instead:
    //
    //   * the game directory. Fabric Loader reads `mods/` from the game directory, and an unpacked
    //     distribution has to find it next to `bin/` (the unpacked root) no matter where the process
    //     is started from. Only the script knows where it lives, and Gradle's template already
    //     resolves that into APP_HOME from the script's own location, so the script passes
    //     `-Dpvz.gameDir=$APP_HOME` (the `.bat`: `%APP_HOME%`). It is a *default*: the script expands
    //     DEFAULT_JVM_OPTS before the user's JAVA_OPTS/PVZ_PORTABLE_OPTS, and a later `-D` of the same
    //     name wins in the JVM, so a user-supplied `-Dpvz.gameDir` overrides it.
    //   * `-XstartOnFirstThread`, which the unix script has to pass on macOS and must not pass
    //     anywhere else (see the application block above). Gradle's script template has no
    //     per-platform hook, so the generated script gets the test appended to the same assignment -
    //     the switch is then part of the same variable the script already launches with, and the
    //     script stays generated (nothing else about it is hand-written). The Windows script needs
    //     none: it never runs on macOS.
    doLast {
        val unixScript = unixScriptFile.get().asFile
        val unixAssignment = unixScript.readLines().first { it.startsWith("DEFAULT_JVM_OPTS=") }
        unixScript.writeText(
            unixScript.readText().replace(
                unixAssignment,
                unixAssignment + "\n" +
                    "# The launcher's game directory is the unpacked root next to bin/ (where Fabric Loader\n" +
                    "# reads mods/), so mods/ is found however this script is started. It is a default: a\n" +
                    "# user's -Dpvz.gameDir in JAVA_OPTS/PVZ_PORTABLE_OPTS comes later and wins.\n" +
                    "DEFAULT_JVM_OPTS=\"\$DEFAULT_JVM_OPTS \\\"-Dpvz.gameDir=\$APP_HOME\\\"\"\n" +
                    "# SDL on macOS needs the JVM's main thread to own the first thread; the switch is\n" +
                    "# a launcher option only macOS's java knows, so it is added on the machine the\n" +
                    "# script runs on and never on the machine that built the zip.\n" +
                    "if [ \"$(uname -s)\" = \"Darwin\" ]; then\n" +
                    "    DEFAULT_JVM_OPTS=\"\$DEFAULT_JVM_OPTS -XstartOnFirstThread\"\n" +
                    "fi",
            ),
        )

        val windowsScript = windowsScriptFile.get().asFile
        val windowsAssignment = windowsScript.readLines().first { it.startsWith("set DEFAULT_JVM_OPTS=") }
        windowsScript.writeText(
            windowsScript.readText().replace(
                windowsAssignment,
                windowsAssignment + "\r\n" +
                    "@rem The launcher's game directory is the unpacked root next to bin/ (where Fabric Loader\r\n" +
                    "@rem reads mods), so mods/ is found however this script is started. It is a default: a\r\n" +
                    "@rem user's -Dpvz.gameDir in JAVA_OPTS/PVZ_PORTABLE_OPTS comes later and wins.\r\n" +
                    "set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% \"-Dpvz.gameDir=%APP_HOME%\"",
            ),
        )
    }
}

// Prints the project version on its own line: a release reads it with `./gradlew -q printVersion` and
// checks it against the tag before creating the release (README.md, "Releases").
tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version, the value a release names the asset after."
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

// The game jar describes itself: the loader reports the game's version through the provider
// (fabric-provider), so this manifest is what a human or a tool reading the artifact - the jar inside
// the release zip, or the one published as cn.enaium.fabricpvz:pvz-portable:<version> - sees.
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "pvz-portable",
            "Implementation-Version" to project.version.toString(),
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    dependsOn(testModsJar)
    // The API and the example mod go into a real mods/ directory for the loader integration test,
    // which forks a second JVM and starts Fabric Loader's KnotClient with this test class path - the
    // class path itself carries neither of them.
    systemProperty("pvz.test.modsDir", testModsDir.get().asFile.absolutePath)
    // The distribution test asserts what the release zip carries - the packaged native libopenmpt
    // libraries of every platform, by entry name and sha256 - so the zip is built before the tests
    // run, and its path is passed in (the test does not guess the version's file name).
    dependsOn(tasks.named("distZip"))
    systemProperty(
        "pvz.test.distribution",
        tasks.named<Zip>("distZip").flatMap { it.archiveFile }.get().asFile.absolutePath,
    )
    // LWJGL uses JNI; JDK 25 warns unless native access is enabled explicitly.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // SDL on macOS needs the first thread; the dummy video driver keeps window creation headless.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
    environment("SDL_VIDEODRIVER", System.getenv("SDL_VIDEODRIVER") ?: "dummy")
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
}

// Visual smoke check: boots the game with the real video driver and writes what the window actually
// shows into build/window-dump/ (see cn.enaium.fabricpvz.portable.test.WindowDump). Useful because the headless
// test run cannot see the window: this is how the "window is black" class of bug gets caught.
tasks.register<JavaExec>("dumpWindow") {
    group = "verification"
    description = "Runs the game with the real video driver and screenshots its window."
    mainClass = "cn.enaium.fabricpvz.portable.test.WindowDump"
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.dir("run").asFile
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Dpvz.gameDir=${layout.projectDirectory.dir("run").asFile.absolutePath}")
    // Forward the diagnostic switches to the forked JVM (Gradle property first, then -D).
    for (switch in listOf("pvz.renderer", "pvz.window.dumpDir")) {
        val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
        if (value != null) {
            systemProperty(switch, value)
        }
    }
    // No audio device in a headless-ish run: sounds would never finish and the cutscenes that
    // wait for them (the level intro) would stall forever.
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Frames of one rendering scene: jumps into the level the scenario needs and dumps the window
// (see cn.enaium.fabricpvz.portable.test.SceneDump). Scenario: graveRise | fume | packet | zomboni.
// The zomboni scenario is the only one that plays the level intro at the game's own pace (it needs
// the mowers the intro places and the board panned back to x=0, see SceneDump.playIntro).
tasks.register<JavaExec>("sceneDump") {
    group = "verification"
    description = "Dumps the window for one SceneDump scenario (-Ppvz.dump.scenario=...)."
    mainClass = "cn.enaium.fabricpvz.portable.test.SceneDump"
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.dir("run").asFile
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Dpvz.gameDir=${layout.projectDirectory.dir("run").asFile.absolutePath}")
    for (switch in listOf("pvz.dump.scenario", "pvz.renderer")) {
        val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
        if (value != null) {
            systemProperty(switch, value)
        }
    }
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Frames of the level-award seed-packet pickup (the packet that grows to 2x while the level fades to
// white): dumps the transition plus the renderer's scaled-draw probe (see
// cn.enaium.fabricpvz.portable.test.AwardPickupDump).
tasks.register<JavaExec>("zoomDump") {
    group = "verification"
    description = "Dumps the award seed-packet pickup transition and probes scaled-draw filtering."
    mainClass = "cn.enaium.fabricpvz.portable.test.AwardPickupDump"
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.dir("run").asFile
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Dpvz.gameDir=${layout.projectDirectory.dir("run").asFile.absolutePath}")
    for (switch in listOf("pvz.renderer", "pvz.zoom.dumpDir", "pvz.zoom.resDir", "pvz.zoom.logClicks")) {
        val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
        if (value != null) {
            systemProperty(switch, value)
        }
    }
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// End-to-end playthrough: plays Adventure mode through the real window with synthetic clicks pushed
// into SDL's event queue (see cn.enaium.fabricpvz.portable.test.AdventurePlaythrough).
tasks.register<JavaExec>("playthrough") {
    group = "verification"
    description = "Plays Adventure mode end to end with real clicks and reports per-level results."
    mainClass = "cn.enaium.fabricpvz.portable.test.AdventurePlaythrough"
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.dir("run").asFile
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Dpvz.gameDir=${layout.projectDirectory.dir("run").asFile.absolutePath}")
    // No audio device in a headless-ish run: sounds would never finish and the cutscenes that
    // wait for them (the level intro) would stall forever.
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    for (switch in listOf("pvz.renderer", "pvz.play.firstLevel", "pvz.play.lastLevel",
                "pvz.play.tickBudget", "pvz.play.fast", "pvz.play.logClicks",
                "pvz.play.firstMode", "pvz.play.lastMode", "pvz.play.fromMenu")) {
        val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
        if (value != null) {
            systemProperty(switch, value)
        }
    }
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Frame-rate benchmark: runs the real loop and reports per-second fps, draw/blit time and GC activity
// (see cn.enaium.fabricpvz.portable.test.FrameBench). Pass -Ppvz.bench.jfr=<file> for a JFR profile.
tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Runs the game loop and reports frame-rate and GC statistics."
    mainClass = "cn.enaium.fabricpvz.portable.test.FrameBench"
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = layout.projectDirectory.dir("run").asFile
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Dpvz.gameDir=${layout.projectDirectory.dir("run").asFile.absolutePath}")
    // No audio device in a headless-ish run: sounds would never finish and the cutscenes that
    // wait for them (the level intro) would stall forever.
    environment("SDL_AUDIODRIVER", System.getenv("SDL_AUDIODRIVER") ?: "dummy")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    for (switch in listOf("pvz.renderer", "pvz.bench.seconds", "pvz.bench.jfr", "pvz.bench.level",
                "pvz.bench.zombies", "pvz.bench.cpuFrames", "pvz.bench.iterations")) {
        val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
        if (value != null) {
            systemProperty(switch, value)
        }
    }
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Optional: copy assets from a local resource directory into the run dir for manual testing.
tasks.register<Copy>("syncDevResources") {
    val resDir = providers.gradleProperty("pvzResDir").orNull
    if (resDir != null) {
        from(resDir) {
            include("main.pak")
            include("properties/**")
        }
        into(layout.projectDirectory.dir("run"))
    }
}

// The development run's game directory, and the one place this script points at it. `run/mods/` is a
// real game directory's mods directory: what the loader loads from there is what the player (or the
// developer) put there by hand. This build installs nothing into it - `run` is the game, and the mods
// of a run are the mods of a run, exactly as in an installation.
val devRunDir = layout.projectDirectory.dir("run")

// The development game directory carries the packaged native libraries at the same relative path the
// distribution uses (`run/lib/native/<os>-<arch>/`), so a dev run resolves libopenmpt exactly like an
// unpacked release does: from the packaged directory when it holds one for this platform, from the
// system library otherwise. `run` is the one game directory the driver tasks below share.
val libopenmptRunNatives = tasks.register<Sync>("libopenmptRunNatives") {
    group = "distribution"
    description = "Stages the packaged native libopenmpt libraries in the development game directory."
    from(libopenmptNatives)
    into(devRunDir.dir("lib/native"))
}

tasks.named<JavaExec>("run") {
    workingDir = devRunDir.asFile
    // The packaged native libraries of every platform this build ships, under run/lib/native/ (see
    // libopenmptRunNatives above): a dev run and an unpacked release have the same game directory
    // layout.
    dependsOn(libopenmptRunNatives)
    // Create the working directory on demand (it holds userdata/, mods/ and, optionally, the assets
    // synced by :syncDevResources).
    doFirst { workingDir.mkdirs() }
    // The game directory; Fabric Loader reads mods/ from it (the launcher's KnotClient is the main
    // class, see `application` above).
    jvmArgs("-Dpvz.gameDir=${devRunDir.asFile.absolutePath}")
    // This port is the only game provider that can be present, so skip Fabric Loader's Minecraft
    // detection pass over the class path.
    jvmArgs("-Dfabric.skipMcProvider=true")
    // A development run: Fabric Loader reports a development environment and also discovers mods that
    // sit on the class path, exactly like the mods' own `./gradlew runClient` (`../loom/`). The mods
    // this run loads are the ones in run/mods/ - the same place a player's installation keeps them.
    jvmArgs("-Dfabric.development=true")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // SDL on macOS requires the JVM's main thread to own the first thread.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
    // The class path is the application's own: the game, the provider (which publishes the real
    // Fabric Loader) and their runtime dependencies. No mod is on it, and no mod is added to it here.
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cn.enaium.fabricpvz"
            artifactId = "pvz-portable"
            // The project version (1.1.0): one value for the jar, the POM and the distribution, so a
            // consumer of `cn.enaium.fabricpvz:pvz-portable:<version>` gets exactly the release zip's
            // game jar.
            version = project.version.toString()
            pom {
                name.set("pvz-portable")
                description.set("Java port of PvZ-Portable (engine + game) using LWJGL/SDL3")
                licenses {
                    license {
                        name.set("LGPL-3.0-or-later")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                    }
                }
            }
        }
    }
}
