package cn.enaium.fabricpvz.build

import java.io.File

/**
 * The empty asset index this package declares and installs.
 *
 * A launcher that is given no asset index of its own falls back to vanilla's `legacy` index, whose
 * `assets/indexes/legacy.json` is not part of a PvZ installation: the launch fails on the missing
 * file, and a launcher that repairs installations before launching downloads the whole index -
 * 1120 objects, 146.4 MB, plus the materialized `virtual` directory - for a port that needs none of
 * it. Declaring an index of this package's own, with the checksum and size of the 14 bytes
 * `{"objects":{}}`, makes that repair a no-op: the file is already in the installation (written by
 * `installMcVersion`), it matches what the json says, so nothing is downloaded and the installation's
 * existing assets are untouched.
 *
 * The file's name carries the version id - `pvz-portable-<version>` - so it sits next to vanilla's
 * indexes without ever colliding with one of them.
 *
 * <p>The url is the repository's copy of those 14 bytes
 * ({@code launcher/empty-asset-index.json}), because a launcher *does* fetch it whenever the
 * installation has no local file or a mismatching one: a hand-unpacked installation (the documented
 * way of installing the version package) has no {@code assets/indexes/} entry until something writes
 * it, and the first version of this declaration pointed at a placeholder host, so HMCL's pre-launch
 * download step failed and the launch never happened. The checksum and size below are what a
 * launcher compares its copy against - either copy is byte-for-byte this content.
 */
object PvzAssetIndex {

    /** The index's content, exactly: `{"objects":{}}`, 14 bytes, no trailing newline. */
    const val CONTENT = """{"objects":{}}"""

    /**
     * The address the json names: this repository's copy of the 14 bytes **at the version's own tag**,
     * which is immutable - the file a launcher fetches can therefore never diverge from the `sha1`
     * and `size` the json declares. The tag is created with the release, and the file is in every
     * commit the release is cut from, so the address resolves as soon as the package is downloadable.
     *
     * <p>It is not a release asset (a release attaches the two packages and nothing else) and not a
     * branch path (a branch is mutable, so a launcher could one day fetch bytes the json no longer
     * describes).
     */
    fun url(version: String): String =
        "https://raw.githubusercontent.com/FabricPvZ/pvz-portable/$version/launcher/empty-asset-index.json"

    /** The id the json declares, the file name's stem, and their content's checksum and length. */
    fun id(version: String): String = "pvz-portable-$version"

    fun fileName(version: String): String = "${id(version)}.json"

    val SHA1: String = sha1Of(CONTENT.toByteArray(Charsets.UTF_8))

    val SIZE: Int = CONTENT.toByteArray(Charsets.UTF_8).size

    /** Where the index lives in a Minecraft installation: `<game dir>/assets/indexes/<id>.json`. */
    fun inGameDir(gameDir: File, version: String): File =
        File(gameDir, "assets/indexes/${fileName(version)}")

    /** Writes the 14 bytes into [file]: how the build materializes them for the packages it writes. */
    fun write(file: File): File {
        file.parentFile.mkdirs()
        file.writeText(CONTENT)
        return file
    }

    /** Writes the index into [gameDir] and returns it. Idempotent: the same 14 bytes every time. */
    fun install(gameDir: File, version: String): File {
        val file = inGameDir(gameDir, version)
        file.parentFile.mkdirs()
        file.writeText(CONTENT)
        return file
    }
}
