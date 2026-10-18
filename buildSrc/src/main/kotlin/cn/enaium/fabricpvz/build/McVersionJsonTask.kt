package cn.enaium.fabricpvz.build

import groovy.json.JsonOutput
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * Writes the version json of the Minecraft-style package: its arguments and every library with the
 * download URL, checksum and size a launcher resolves it by.
 *
 * The json is *written*, not concatenated: the same map is handed to the build's JSON writer
 * (Groovy's `JsonOutput`, part of the Gradle API), which is what makes the file's key order and
 * escaping stable, and what makes the whole file reproducible from its inputs. The one thing that
 * is not derived from an input is the build time the json stamps into `time`/`releaseTime`, exactly
 * as the build script did before this task type existed.
 *
 * An empty asset index is declared so that a launcher never looks for - and never downloads - the
 * 146 MB vanilla `legacy` index this port has no use for: the id is this package's own, the checksum
 * and size are the ones of the 14 bytes [PvzAssetIndex] writes into the installation, and a launcher
 * that finds that file (which `installMcVersion` puts there) has nothing to download.
 */
abstract class McVersionJsonTask : DefaultTask() {

    /** The version id: the json's `id`, and the name of its version folder. */
    @get:Input
    abstract val versionId: Property<String>

    /** The id of the empty asset index this package declares ([PvzAssetIndex]). */
    @get:Input
    abstract val assetIndexId: Property<String>

    /** What a launcher starts ([McPackage.MAIN_CLASS]). */
    @get:Input
    abstract val mainClass: Property<String>

    /** The Java the game needs ([McPackage.JAVA_MAJOR_VERSION]). */
    @get:Input
    abstract val javaMajorVersion: Property<Int>

    /** The `libraries` entries, already derived from the runtime class path. */
    @get:Input
    abstract val libraries: ListProperty<McLibrary>

    /**
     * The runtime class path the entries were derived from: the files themselves are inputs too, so
     * a jar that moves re-runs the writer even if its coordinate did not change.
     */
    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeVersionJson() {
        val id = versionId.get()
        val time = OffsetDateTime.now().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val libraries = libraries.get()
        val json = linkedMapOf<String, Any?>(
            "id" to id,
            "type" to "release",
            "time" to time,
            "releaseTime" to time,
            "minimumLauncherVersion" to 21,
            "mainClass" to mainClass.get(),
            "assetIndex" to linkedMapOf(
                "id" to assetIndexId.get(),
                "url" to PvzAssetIndex.URL,
                "sha1" to PvzAssetIndex.SHA1,
                "size" to PvzAssetIndex.SIZE,
            ),
            // The game needs Java 25 (its class files are); 25 is the same requirement the
            // distribution's start script has.
            "javaVersion" to linkedMapOf(
                "component" to "java-runtime-epsilon",
                "majorVersion" to javaMajorVersion.get(),
            ),
            "arguments" to linkedMapOf(
                "jvm" to listOf(
                    // SDL needs the JVM's first thread on macOS (the start script of the
                    // distribution adds the same switch on that machine only).
                    mapOf(
                        "rules" to listOf(mapOf("action" to "allow", "os" to mapOf("name" to "osx"))),
                        "value" to listOf("-XstartOnFirstThread"),
                    ),
                    "--enable-native-access=ALL-UNNAMED",
                    // This port is the only game provider that can be present.
                    "-Dfabric.skipMcProvider=true",
                    // The provider reads the game directory from this property (the loader takes
                    // mods/ from there), the same way the distribution's start script and the run
                    // tasks pass it. It must not be a game argument: the provider accepts
                    // `-gamedir <path>` too, but it does not strip it, and the game's own command
                    // line parser (upstream's) exits on any parameter it does not know.
                    "-Dpvz.gameDir=\${game_directory}",
                    // The launcher substitutes this pair with the class path it resolved from this
                    // json's `libraries` (every version json carries it - the official one and the
                    // Fabric-installed one, in this position, `-cp` last). Without it the generated
                    // command has no class path at all and the JVM fails on the main class.
                    "-cp",
                    "\${classpath}",
                ),
                "game" to listOf(
                    // The game's own userdata, next to the game directory the provider reads from
                    // -Dpvz.gameDir above. This one *is* a game argument: the game parses it itself.
                    "-savedir", "\${game_directory}/pvz-userdata",
                ),
            ),
            "libraries" to libraries.map { it.toJson() },
        )
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n")
        logger.lifecycle("mcVersionJson: {} ({} libraries)", file, libraries.size)
    }
}

/**
 * Registers `mcVersionJson` on this project: the version id, the asset index it declares and the
 * output path are the script's declaration, the json's content is [McVersionJsonTask]'s.
 */
fun Project.registerMcVersionJson(versionId: String, assetIndexId: String): TaskProvider<McVersionJsonTask> {
    val runtimeClasspath = configurations.named("runtimeClasspath")
    val output = layout.buildDirectory.file("mcversion/$versionId/$versionId.json")
    return tasks.register("mcVersionJson", McVersionJsonTask::class.java) {
        group = "distribution"
        description = "Writes the Minecraft-style version json: arguments and libraries with download URLs."
        this.versionId.set(versionId)
        this.assetIndexId.set(assetIndexId)
        mainClass.set(McPackage.MAIN_CLASS)
        javaMajorVersion.set(McPackage.JAVA_MAJOR_VERSION)
        // Resolving the class path (and hashing its jars) happens when the task's inputs are
        // snapshotted, not while the build is configured: a `help` run resolves nothing.
        libraries.set(runtimeClasspath.map { McPackage.libraries(it) })
        this.runtimeClasspath.from(runtimeClasspath)
        outputFile.set(output)
    }
}
