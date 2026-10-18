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
     * The address the json names: the release's own copy of the 14 bytes, uploaded as
     * {@code pvz-portable-<version>-asset-index.json} next to the two packages.
     *
     * <p>It is a release asset because that is the only address that is ours, immutable and always
     * reachable: a raw path on a branch is mutable and needs a file in the repository, a raw path
     * under a tag exists only if that tag's commit happens to carry the file (the tag of a release cut
     * after the file was removed does not, and the URL 404s - a launcher that then finds nothing else
     * aborts the launch, which is the failure this whole declaration exists to prevent), and a vanilla
     * asset index is 146.4 MB of assets this port never uses.
     *
     * <p>The bytes never change between versions, but the address is per version, so a release stays
     * self-describing: what the json declares is what its own release serves.
     */
    fun url(version: String): String =
        "https://github.com/" + REPOSITORY + "/releases/download/" + version + "/" +
            ASSET_PREFIX + version + "-asset-index.json"

    /** The repository the releases (and the asset above) belong to. */
    const val REPOSITORY = "FabricPvZ/pvz-portable"

    /** The name of the release asset that carries the 14 bytes. */
    fun assetName(version: String): String = ASSET_PREFIX + version + "-asset-index.json"

    /** The directory of a source checkout / the release's own asset prefix. */
    const val ASSET_PREFIX = "pvz-portable-"

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
