import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    id("java-library")
    application
    `maven-publish`
}

group = "cn.enaium.fabricpvz"
// The game's version: 1.0.0, the version every artifact of this workspace carries (the loader, the
// API, the toolchain and the mods are all 1.0.0 too - the port's own version is independent of the
// upstream C++ project's tags). It is also the version a release reads (see the `printVersion` task)
// and the name of the distribution zip (`pvz-portable-<version>.zip`).
version = "1.0.0"

// The artifact's own name, matching the Maven artifactId (`cn.enaium.fabricpvz:pvz-portable`), the
// distribution and the release asset: `build/libs/pvz-portable-1.0.0.jar`, `lib/pvz-portable-1.0.0.jar`
// inside the zip, `~/.m2/repository/cn/enaium/fabricpvz/pvz-portable/1.0.0/pvz-portable-1.0.0.jar`.
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

// The example mod jar is resolved from mavenLocal and copied into build/testmods for the
// end-to-end mod-loading test. It is deliberately NOT on the test classpath: mods load from the
// mods directory exactly like they do in a real installation.
val modsForTest by configurations.creating

// The API is a *mod* of the game directory, and never a dependency of this project.
//
// Fabric parity is why it cannot be one: fabric-api depends on this game in turn (its event types
// are typed against the game's classes), so the two projects cannot depend on each other. More to the
// point, the loader only ever gets the API from the `mods/` directory of the game directory it
// launches on, exactly like it gets every mod a player installs - so a dependency here would be the
// wrong shape even if the cycle allowed it. The API's jars are therefore plain files of this
// workspace's own build of it (`../fabric-api/build/libs/`) and not a resolvable configuration: a
// configuration would hand `javac` the classes (`testCompileOnly`) while telling the loader nothing,
// it would let the coordinate resolve from mavenLocal while the run had no jar to load, and a missing
// API build would surface as a hundred "cannot find symbol" errors instead of the command that fixes
// it (see [checkApiJars]).
//
// Two shapes of the same artifacts are needed, and they are not interchangeable:
//  * the aggregator, `cn.enaium.fabricpvz:fabric-api` (`fabric-api-<version>.jar`), is the whole API
//    as **one mod**: its four feature modules sit inside it as `META-INF/jars/*.jar` and the loader
//    loads them from there. This is the jar that goes into a `mods/` directory (the dev `run` and the
//    game dir of an installation alike), and the only API jar a player ever copies ([apiModJars]);
//  * the four feature modules (`api-base`, `api-events`, `api-plant`, `api-zombie`) are what `javac`
//    needs ([apiModuleJars]): a class inside a nested jar is not on a compile class path, so the test
//    fixtures - the only sources of this project that import `cn.enaium.fabricpvz.api.*` - compile
//    against the modules themselves.
val fabricApiDir = File(rootDir.parentFile, "fabric-api")

/** The API as one mod: a jar for a `mods/` directory, never for a class path. */
val apiModJars = fileTree(File(fabricApiDir, "build/libs")) {
    include("fabric-api-*.jar")
    exclude("*-sources.jar", "*-javadoc.jar")
}

/** The four feature modules: for `javac` only (see above). */
val apiModuleJars = fileTree(fabricApiDir) {
    include("api-*/build/libs/api-*.jar")
    exclude("**/*-sources.jar", "**/*-javadoc.jar")
}

/** The four modules the API is made of, for the check below and for the failure message. */
val apiModuleNames = listOf("api-base", "api-events", "api-plant", "api-zombie")

/**
 * Fails with the command that fixes a missing API build, instead of a failure further down that says
 * nothing about the cause: `javac` reporting every API type the fixtures import as an unknown package,
 * a `Sync` with nothing to copy (which Gradle skips as `NO-SOURCE`), or a loader that cannot find the
 * dependency of every mod it just read from `run/mods/`. `mods/` is where the loader reads mods from,
 * so there is nothing here to resolve on demand - the API has to have been built.
 */
fun checkApiJars(modJars: Collection<File>, moduleJars: Collection<File>) {
    val missing = when {
        modJars.isEmpty() -> "the aggregate jar fabric-api-<version>.jar"
        moduleJars.size != apiModuleNames.size -> "the module jars " + apiModuleNames.joinToString(", ")
        else -> return
    }

    throw GradleException(
        "The API is not built: ${fabricApiDir.absolutePath} does not contain $missing. This project uses " +
            "the API as a mod - a jar installed into a `mods/` directory and never a dependency - so it does " +
            "not resolve it from a repository:\n" +
            "    (cd ../fabric-api && ./gradlew build)\n" +
            "    # that build needs this project's POM in mavenLocal: ./gradlew publishToMavenLocal -x test\n" +
            "README.md §3 has the whole order."
    )
}

/**
 * The check as a task: the copy tasks below cannot carry it themselves, because Gradle skips a task
 * with no source as `NO-SOURCE` before any of its actions run - which is exactly the case a missing
 * API jar would create.
 */
val checkApiBuilt = tasks.register("checkApiBuilt") {
    group = "verification"
    description = "Fails when the API has not been built in this workspace (see apiModJars)."
    // The API's jars are this task's inputs, so rebuilding or removing one re-runs the check.
    inputs.files(apiModJars, apiModuleJars).withPropertyName("apiJars")
    doLast { checkApiJars(apiModJars.files, apiModuleJars.files) }
}

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
    runtimeOnly("cn.enaium.fabricpvz:fabric-provider:1.0.0")

    // The game's main sources are Fabric-free; its test fixtures are mods, so they compile against
    // the loader (ModInitializer) and query it (FabricLoader facade) when they run.
    testImplementation(libs.fabric.loader)

    // Fabric parity: fabric-api depends on this game (its events are typed against it), so it is not
    // a dependency of this project in either direction - it is a mod, loaded from `mods/` (see
    // [apiModJars] above). The fixtures (`src/test/java/.../contentmodfixture/`) are mods in
    // disguise and import the API's public types, so their *compilation* gets the API's four feature
    // modules as plain files of the API build in this workspace - compile-only, and deliberately not
    // `testRuntimeOnly`: nothing of the test class path may carry the API, because the game has to
    // find it exactly where a player's installation keeps it, in the mods directory the loader reads
    // ([testModsJar] below for the loader test, [installApiMod] for the dev run).
    testCompileOnly(apiModuleJars)

    modsForTest("cn.enaium.fabricpvz:pvz-example-mod:1.0.0")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val testModsDir = layout.buildDirectory.dir("testmods")

// The mods the loader end-to-end test finds in a real `mods/` directory: the example mod (resolved
// from mavenLocal, see `modsForTest` above) and the API's aggregate jar (built in this workspace, see
// [apiModJars]). One `Sync` for both, because `build/testmods` is Gradle's own directory: two syncs
// into it would prune each other's jars. The test reads the directory through the `pvz.test.modsDir`
// system property the test task sets below.
val testModsJar = tasks.register<Sync>("testModsJar") {
    group = "verification"
    description = "Installs the mods the loader end-to-end test loads into build/testmods."
    dependsOn(checkApiBuilt)
    from(configurations.named("modsForTest"))
    from(apiModJars)
    into(testModsDir)
}

application {
    // Fabric Loader's launcher, exactly as the `run` task uses it: it finds this game through the
    // provider module's GameProvider and loads the game directory's mods/ before calling the game's
    // main class. `run` and the distribution therefore launch the same entry point, in the same
    // order, and the distribution's `bin/pvz-portable` script is just that launch without Gradle.
    mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient"
    // The dist directory (and the script names) inside the zip: `pvz-portable-1.0.0/bin/pvz-portable`.
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

// The fixtures are the only sources here that import the API (`contentmodfixture/`), and they get it
// through plain files, so nothing would fail on its own if the API had not been built: `javac` would
// report every `cn.enaium.fabricpvz.api` import as an unknown package, which says nothing about the
// cause. Check first, compile after.
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(checkApiBuilt)
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

// Installs the API into the game directory's mods/ directory, so that `./gradlew run` gets the API
// the way an installation does: a jar the loader discovers in `mods/`, never a jar on the class path.
//
// A `Copy` and not a `Sync` - and this is the one place this project differs from what a mod project's
// `pvzModsJar` does with its own `run/`: `run/mods/` is a real game directory's mods directory, so it
// also holds the mods a developer installed by hand (run/ is in .gitignore; the workspace keeps e.g.
// the speed mod or a Kotlin language adapter there). A `Sync` would delete every jar it did not put
// there itself. What a `Sync` is wanted for here is only its pruning of *the API's own* previous
// jars, so a version bump does not leave two `fabric-api-*.jar` files - two mods with one id - in the
// directory; that is done below, by name, and nothing else in run/mods/ is touched.
val devRunDir = layout.projectDirectory.dir("run")

val installApiMod = tasks.register<Copy>("installApiMod") {
    group = "application"
    description = "Copies the built API jar into run/mods, where Fabric Loader reads mods from."
    dependsOn(checkApiBuilt)
    from(apiModJars)
    into(devRunDir.dir("mods"))
    doLast {
        val installed = apiModJars.files.map { it.name }.toSet()
        val stale = devRunDir.dir("mods").asFile
            .listFiles { file -> file.name.endsWith(".jar") && file.name.startsWith("fabric-api-") && file.name !in installed }
            .orEmpty()

        for (jar in stale) {
            jar.delete()
            logger.lifecycle("installApiMod: removed the previous API jar ${jar.name}")
        }
    }
}

tasks.named<JavaExec>("run") {
    workingDir = devRunDir.asFile
    // Create the working directory on demand (it holds userdata/, mods/ and, optionally, the assets
    // synced by :syncDevResources).
    doFirst { workingDir.mkdirs() }
    // The API, in run/mods/ before the loader starts (see `installApiMod` above): the API is a mod,
    // and a mod the game directory does not contain is a mod the loader does not load.
    dependsOn(installApiMod)
    // The game directory; Fabric Loader reads mods/ from it (the launcher's KnotClient is the main
    // class, see `application` above).
    jvmArgs("-Dpvz.gameDir=${devRunDir.asFile.absolutePath}")
    // This port is the only game provider that can be present, so skip Fabric Loader's Minecraft
    // detection pass over the class path.
    jvmArgs("-Dfabric.skipMcProvider=true")
    // A development run: Fabric Loader reports a development environment and also discovers mods that
    // sit on the class path, exactly like the mods' own `./gradlew runClient` (`../loom/`). It is no
    // longer what gives this run an API - the API is a jar in run/mods/, which is where a player's
    // installation keeps it too.
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
            // The project version (1.0.0): one value for the jar, the POM and the distribution, so a
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
