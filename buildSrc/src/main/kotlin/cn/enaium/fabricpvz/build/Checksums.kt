package cn.enaium.fabricpvz.build

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The sha256 of [file], as the identity of what was downloaded and of what was extracted.
 *
 * The libopenmpt pin (`Libopenmpt`) is a sha256 of the archive and of every member the build stages
 * out of it, so this is how a download and an extraction are checked against the pin.
 */
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

/** The sha1 of [file], the checksum a Minecraft launcher verifies a library with. */
fun sha1Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}

/** The sha1 of [bytes], for the handful of bytes a launcher compares without a file on disk. */
fun sha1Of(bytes: ByteArray): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes))
