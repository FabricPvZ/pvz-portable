package cn.enaium.fabricpvz.build

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * libopenmpt: the optional native MO3 decoder, shipped inside the distribution.
 *
 * The retail music is the only module audio the game has - sounds/MAINMUSIC.MO3 and
 * sounds/MAINMUSIC_HIHATS.MO3 of main.pak; every other piece of music and every sound effect is Ogg
 * Vorbis or AU and decoded in Java - and only libopenmpt decodes MO3. It stays an *optional*
 * dependency: the port probes the game directory's own native directory first and falls back to the
 * system library (Homebrew, apt, ...), so a machine without it keeps playing everything except the
 * MO3 music (see `OpenMptLibrary`).
 *
 * Upstream publishes compiled binaries for Windows only: lib.openmpt.org/libopenmpt/download lists
 * Windows arm64/amd64/x86 builds and, for every other operating system, "distribution packages".
 * This build therefore ships the two Windows builds and macOS/Linux keep the system lookup. The
 * archive below is the official development-libs package of the pinned release: it holds the
 * per-architecture DLLs (with the import libraries and headers, which the distribution does not
 * carry) and the OpenMPT licence.
 *
 * The five DLLs of a platform are one unit: libopenmpt.dll *statically* imports openmpt-mpg123.dll,
 * openmpt-vorbis.dll and openmpt-zlib.dll, and openmpt-vorbis.dll imports openmpt-ogg.dll. They stay
 * in the one directory of the platform - the port loads the satellites from there by absolute path
 * before loading libopenmpt.dll itself, which is what makes the packaged copy work on a machine
 * where nothing else puts that directory on the DLL search path.
 *
 * No binary is committed to git: the build downloads the pinned archive once into the Gradle user
 * home, verifies the archive's sha256 and the sha256 of every member it extracts, and stages what
 * the distribution and the development run carry. A build without network and without the cached
 * archive fails with the URL instead of silently producing a release zip without the libraries;
 * with the archive cached, `--offline` builds work.
 */
object Libopenmpt {

    /** The libopenmpt release whose official Windows binaries the distribution carries. */
    const val VERSION = "0.8.9"

    /** The archive's name, its address, and the identity of the one download this build makes. */
    const val ARCHIVE_NAME = "libopenmpt-$VERSION+release.dev.windows.vs2022.zip"
    const val ARCHIVE_URL = "https://lib.openmpt.org/files/libopenmpt/dev/$ARCHIVE_NAME"
    const val ARCHIVE_SHA256 = "a23e375e576e3a6997ffeeb6cf34488e9020e2a5d8226856b02628b6a87c5e8e"

    /** The directory inside the archive each platform's DLLs come from. */
    val ARCHIVE_DIRECTORIES = mapOf("windows-x64" to "bin/amd64", "windows-arm64" to "bin/arm64")

    /**
     * What each platform directory of `lib/native/` carries, in load order (dependencies first),
     * with the sha256 the extracted file has in the pinned archive. The archive's sha256 above pins
     * the download; these pin what the build puts in the zip, and the distribution test asserts the
     * zip's entries against them.
     */
    val LIBRARIES: Map<String, Map<String, String>> = linkedMapOf(
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
    const val LICENCE_SHA256 = "2c999e99533c10a347916a72e075bacf10fcedeff793bd90d41d812b46161da2"

    /** The licence's name in the staged directory, and the manifest naming the pinned archive. */
    const val LICENCE_NAME = "libopenmpt-LICENSE.txt"
    const val MANIFEST_NAME = "libopenmpt-natives.txt"

    /** Where the pinned archive is cached in the Gradle user home. */
    fun archiveIn(gradleUserHome: File): File = File(gradleUserHome, "caches/pvz/libopenmpt/$ARCHIVE_NAME")
}

/**
 * Makes sure the pinned archive is in [target] and returns whether it had to be downloaded. The
 * download lands as a temporary file next to it and is moved into place only once it is complete,
 * so an interrupted download never leaves a truncated archive behind (the same shape as the
 * toolchain's own distribution download, `../loom`).
 */
fun fetchLibopenmptArchive(url: String, target: Path, offline: Boolean, userAgent: String): Boolean {
    if (Files.isRegularFile(target)) {
        return false
    }
    if (offline) {
        throw GradleException(
            "libopenmpt ${Libopenmpt.VERSION} is not cached ($target) and Gradle runs with --offline," +
                " so the official release binaries cannot be downloaded. Run once without --offline" +
                " (the archive is then cached for every later build), or download $url into that" +
                " file by hand.",
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
            .header("User-Agent", userAgent)
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
                " libraries are part of the distribution, so this build fails instead of producing" +
                " a release zip without them.",
            exception,
        )
    }
    return true
}

/**
 * Downloads the pinned official libopenmpt binaries (sha256-verified) and stages `lib/native/`:
 * `<platform>/` directories, the licence, and a manifest naming the archive every file comes from -
 * the layout the distribution ships and the development run carries.
 *
 * The pin is what the task is about, so the version, the archive's identity and the hashes of the
 * members it extracts are inputs: changing one of them re-runs the task, and an unchanged pin with
 * an intact stage directory leaves it up to date (and cacheable). The cached archive in the Gradle
 * user home is where this task's download lands rather than an input of it, and `--offline` is a
 * start parameter like it was before, so neither is tracked here.
 */
abstract class LibopenmptNativesTask : DefaultTask() {

    @get:Input
    abstract val libopenmptVersion: Property<String>

    @get:Input
    abstract val libopenmptArchiveSha256: Property<String>

    @get:Input
    abstract val archiveUrl: Property<String>

    /** `platform -> (directory inside the archive)`. */
    @get:Input
    abstract val archiveDirectories: MapProperty<String, String>

    /** `platform -> (file name -> sha256)`, in load order. */
    @get:Input
    abstract val libraries: MapProperty<String, Map<String, String>>

    @get:Input
    abstract val licenceSha256: Property<String>

    /** The cached archive, where this task's download lands. */
    @get:Internal
    abstract val archive: RegularFileProperty

    @get:Internal
    abstract val offline: Property<Boolean>

    @get:Internal
    abstract val userAgent: Property<String>

    /** The staged `lib/native/`. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val version = libopenmptVersion.get()
        val url = archiveUrl.get()
        val pinnedArchiveSha256 = libopenmptArchiveSha256.get()
        val file = archive.get().asFile
        val target = outputDirectory.get().asFile

        if (fetchLibopenmptArchive(url, file.toPath(), offline.get(), userAgent.get())) {
            logger.lifecycle("libopenmpt {}: downloaded {} from {}", version, file.name, url)
        }

        val archiveSha256 = sha256Of(file.toPath())
        if (!archiveSha256.equals(pinnedArchiveSha256, ignoreCase = true)) {
            // A wrong hash means the cached file is not the archive this build pinned (an interrupted
            // download that somehow moved into place, a partial copy, or a changed upstream file):
            // drop it and say so, instead of packaging something else under the pinned identity.
            file.delete()
            throw GradleException(
                "libopenmpt $version: $file has sha256 $archiveSha256 instead of the pinned" +
                    " $pinnedArchiveSha256. The file was deleted; a build with network downloads" +
                    " $url again.",
            )
        }

        target.deleteRecursively()
        Files.createDirectories(target.toPath())
        val members = StringBuilder()
        ZipFile(file).use { zip ->
            for ((platform, hashByName) in libraries.get()) {
                val archiveDirectory = archiveDirectories.get().getValue(platform)
                val platformDirectory = target.resolve(platform)
                Files.createDirectories(platformDirectory.toPath())
                for ((name, expected) in hashByName) {
                    val entry = zip.getEntry("$archiveDirectory/$name")
                        ?: throw GradleException(
                            "libopenmpt $version: ${file.name} carries no $archiveDirectory/$name.",
                        )
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(platformDirectory.resolve(name).toPath()).use { input.copyTo(it) }
                    }
                    val sha256 = sha256Of(platformDirectory.resolve(name).toPath())
                    if (!sha256.equals(expected, ignoreCase = true)) {
                        throw GradleException(
                            "libopenmpt $version: $archiveDirectory/$name was extracted with" +
                                " sha256 $sha256 instead of the pinned $expected.",
                        )
                    }
                    members.append(platform).append(' ').append(name).append(' ').append(sha256).append('\n')
                }
            }

            val licence = zip.getEntry("LICENSE.txt")
                ?: throw GradleException("libopenmpt $version: ${file.name} carries no LICENSE.txt.")
            zip.getInputStream(licence).use { input ->
                Files.newOutputStream(target.resolve(Libopenmpt.LICENCE_NAME).toPath()).use { input.copyTo(it) }
            }
        }

        val pinnedLicenceSha256 = licenceSha256.get()
        val licenceSha256 = sha256Of(target.resolve(Libopenmpt.LICENCE_NAME).toPath())
        if (!licenceSha256.equals(pinnedLicenceSha256, ignoreCase = true)) {
            throw GradleException(
                "libopenmpt $version: the licence was extracted with sha256 $licenceSha256" +
                    " instead of the pinned $pinnedLicenceSha256.",
            )
        }

        Files.writeString(
            target.resolve(Libopenmpt.MANIFEST_NAME).toPath(),
            "libopenmpt " + version + "\n" +
                "url=" + url + "\n" +
                "archive-sha256=" + pinnedArchiveSha256 + "\n" +
                "# Windows binaries only: upstream publishes no macOS or Linux binaries (see" +
                " lib.openmpt.org/libopenmpt/download, where every other operating system is" +
                " \"distribution packages\"), so those platforms use the system library.\n" +
                "# platform file sha256\n" +
                members,
        )
        logger.lifecycle(
            "libopenmpt {}: staged {} ({} files, {} platforms)",
            version,
            target,
            libraries.get().values.sumOf { it.size } + 2,
            libraries.get().size,
        )
    }
}

/**
 * Registers `libopenmptNatives` on this project. The script declares the task; everything the task
 * is - the pin, the cache, the staged directory and the offline rule - comes from [Libopenmpt].
 */
fun Project.registerLibopenmptNatives(): TaskProvider<LibopenmptNativesTask> {
    val userAgent = "pvz-portable/$version"
    val cachedArchive = Libopenmpt.archiveIn(gradle.gradleUserHomeDir)
    val startParameter = gradle.startParameter
    val stageDirectory = layout.buildDirectory.dir("libopenmpt-native")
    return tasks.register("libopenmptNatives", LibopenmptNativesTask::class.java) {
        group = "distribution"
        description = "Downloads the pinned official libopenmpt binaries (sha256-verified) and stages lib/native/."
        libopenmptVersion.set(Libopenmpt.VERSION)
        libopenmptArchiveSha256.set(Libopenmpt.ARCHIVE_SHA256)
        archiveUrl.set(Libopenmpt.ARCHIVE_URL)
        archiveDirectories.set(Libopenmpt.ARCHIVE_DIRECTORIES)
        libraries.set(Libopenmpt.LIBRARIES)
        licenceSha256.set(Libopenmpt.LICENCE_SHA256)
        archive.set(cachedArchive)
        offline.set(startParameter.isOffline)
        this.userAgent.set(userAgent)
        outputDirectory.set(stageDirectory)
    }
}
