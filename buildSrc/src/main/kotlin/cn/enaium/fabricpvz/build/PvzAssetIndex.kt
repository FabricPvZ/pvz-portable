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
 * indexes without ever colliding with one of them. Its url is never fetched for the reason above:
 * it exists so the json is a complete asset index declaration, and a launcher only goes to it if the
 * installed file is missing or does not match the sha1/size below (in which case a port without any
 * asset objects has nothing better to offer anyway).
 */
object PvzAssetIndex {

    /** The index's content, exactly: `{"objects":{}}`, 14 bytes, no trailing newline. */
    const val CONTENT = """{"objects":{}}"""

    /** The address the json names. Never fetched: the file `installMcVersion` writes matches it. */
    const val URL = "https://example.invalid/empty-asset-index.json"

    /** The id the json declares, the file name's stem, and their content's checksum and length. */
    fun id(version: String): String = "pvz-portable-$version"

    fun fileName(version: String): String = "${id(version)}.json"

    val SHA1: String = sha1Of(CONTENT.toByteArray(Charsets.UTF_8))

    val SIZE: Int = CONTENT.toByteArray(Charsets.UTF_8).size

    /** Where the index lives in a Minecraft installation: `<game dir>/assets/indexes/<id>.json`. */
    fun inGameDir(gameDir: File, version: String): File =
        File(gameDir, "assets/indexes/${fileName(version)}")

    /** Writes the index into [gameDir] and returns it. Idempotent: the same 14 bytes every time. */
    fun install(gameDir: File, version: String): File {
        val file = inGameDir(gameDir, version)
        file.parentFile.mkdirs()
        file.writeText(CONTENT)
        return file
    }
}
