package cn.enaium.fabricpvz.build

import java.io.File
import java.io.Serializable
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

/**
 * The Minecraft-client-style version package: one zip whose content is the version folder a
 * Minecraft launcher reads out of `versions/`.
 *
 *     PvZ-Portable-<version>/PvZ-Portable-<version>.jar    the game, with the provider bundled - the
 *                                                         provider is this workspace's own launcher
 *                                                         glue and has no download address of its own
 *     PvZ-Portable-<version>/PvZ-Portable-<version>.json   main class, launch arguments and every
 *                                                         dependency by name and download URL
 *
 * The zip carries **no** dependency file: a launcher reads the json and downloads the libraries it
 * names (the real Fabric Loader with Mixin and ASM, the SLF4J/Log4j stack, and LWJGL with the native
 * libraries of every platform the distribution ships) from Maven Central and Fabric's maven, which is
 * why one version package runs on every platform the distribution runs on instead of only on the
 * machine that built it. `installMcVersion` unpacks it into an installation's `versions/` and copies
 * a mods directory into that installation's `mods/`.
 *
 * Mirrored on a real (Fabric-installed) version json, so a launcher that understands vanilla
 * understands this: `libraries` entries carry `downloads.artifact` with path/url/sha1/size, natives
 * are classifier libraries with an OS-and-architecture rule, and the version id, folder and file
 * names are one name.
 *
 * Everything here is build logic (the rules, the URL shape, the launch arguments, how a library
 * entry is derived from a resolved artifact), which is why it is not in the build script: the script
 * declares the tasks and their parameters.
 */
object McPackage {

    /** Fabric Loader's launcher: what a launcher starts, in the distribution and in this package. */
    const val MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient"

    /** The Java the game needs (its class files are): the json's `javaVersion`, the toolchain's. */
    const val JAVA_MAJOR_VERSION = 25

    /**
     * Native classifier to the operating system *and* architecture that take it. `osx` covers macOS
     * on both architectures (`name` is the launcher's own name for the system, and macOS is `osx`),
     * and the arch values are the ones a launcher matches: it compares `os.arch` of the machine -
     * `arm64` on Apple Silicon and on arm64 Linux/Windows, `x86_64` on the x64 builds - so exactly
     * one of the two classifiers a platform publishes applies. Without the arch the x64 and the
     * arm64 build of every LWJGL module both apply, and the launcher downloads and unpacks both into
     * the natives directory when only one of them can ever be loaded.
     */
    val nativeRules = mapOf(
        "natives-macos" to ("osx" to "x86_64"),
        "natives-macos-arm64" to ("osx" to "arm64"),
        "natives-linux" to ("linux" to "x86_64"),
        "natives-linux-arm64" to ("linux" to "arm64"),
        "natives-windows" to ("windows" to "x86_64"),
        "natives-windows-arm64" to ("windows" to "arm64"),
    )

    /** Where a library is downloaded from: Fabric's maven for the loader, Maven Central for the rest. */
    fun mavenBase(group: String): String =
        if (group.startsWith("net.fabricmc")) "https://maven.fabricmc.net/" else "https://repo1.maven.org/maven2/"

    /**
     * The library entries of the version json: the runtime class path, minus what the package
     * carries. One entry per jar, in file-name order - the order the json lists them in and the order
     * a launcher resolves them in, so the file is the same file wherever and whenever it is written.
     */
    fun libraries(runtimeClasspath: Configuration): List<McLibrary> {
        val libraries = mutableListOf<McLibrary>()
        runtimeClasspath.incoming.artifacts.resolvedArtifacts.get()
            .sortedBy { it.file.name }
            .forEach { artifact ->
                val identifier = artifact.id.componentIdentifier
                if (identifier !is ModuleComponentIdentifier) {
                    return@forEach
                }
                val group = identifier.group
                val module = identifier.module
                val libraryVersion = identifier.version
                // the game is the version jar itself; the provider rides inside it (see the header)
                if (group == "cn.enaium.fabricpvz" && (module == "pvz-portable" || module == "fabric-provider")) {
                    return@forEach
                }
                val fileName = artifact.file.name
                val classifier = fileName
                    .removeSuffix(".jar")
                    .removePrefix("$module-$libraryVersion")
                    .removePrefix("-")
                val path = "${group.replace('.', '/')}/$module/$libraryVersion/$fileName"
                val rule = nativeRules[classifier]
                libraries += McLibrary(
                    name = if (classifier.isEmpty()) "$group:$module:$libraryVersion"
                    else "$group:$module:$libraryVersion:$classifier",
                    path = path,
                    url = mavenBase(group) + path,
                    sha1 = sha1Of(artifact.file),
                    size = artifact.file.length(),
                    os = rule?.first,
                    arch = rule?.second,
                )
            }
        return libraries
    }
}

/**
 * One `libraries` entry of the version json, as the launcher reads it: the coordinate, the artifact's
 * download address and checksum, and - for a natives classifier - the OS-and-architecture rule that
 * decides whether it applies to the machine the game is launched on.
 *
 * Serializable because it is a task input: the list of these is what the json is written *from*, so
 * changing the runtime class path (or the jar behind one of its coordinates) re-runs the writer.
 */
data class McLibrary(
    val name: String,
    val path: String,
    val url: String,
    val sha1: String,
    val size: Long,
    val os: String?,
    val arch: String?,
) : Serializable {

    /** The entry, in the launcher's own key order. */
    fun toJson(): Map<String, Any> {
        val entry = linkedMapOf<String, Any>(
            "name" to name,
            "downloads" to mapOf(
                "artifact" to linkedMapOf(
                    "path" to path,
                    "url" to url,
                    "sha1" to sha1,
                    "size" to size,
                ),
            ),
        )
        if (os != null && arch != null) {
            entry["rules"] = listOf(
                mapOf("action" to "allow", "os" to linkedMapOf("name" to os, "arch" to arch)),
            )
        }
        return entry
    }
}
