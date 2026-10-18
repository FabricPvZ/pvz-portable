package cn.enaium.fabricpvz.build

/**
 * What the distribution ships of LWJGL: the modules the game uses, the one artifact coordinate per
 * module and platform, and the platforms themselves.
 *
 * All of it is build logic rather than a declaration of this project's dependencies - the lists are
 * the same six modules and six platforms for every module - so it lives here and the build script
 * only says which modules it consumes.
 */
object Lwjgl {

    /** The LWJGL modules the game uses, in the order the distribution's class path carries them. */
    val artifacts = listOf("lwjgl", "lwjgl-sdl", "lwjgl-opengl", "lwjgl-openal", "lwjgl-stb", "lwjgl-nfd")

    /**
     * The platforms the distribution ships native libraries for: Windows, Linux and macOS on x64 and
     * arm64. The list is written out rather than derived from the build machine, because the release
     * zip has to be the same zip wherever it is built: these are LWJGL's published natives
     * classifiers, and they are the same six for every LWJGL module this game uses (`lwjgl`,
     * `lwjgl-sdl`, `lwjgl-opengl`, `lwjgl-openal`, `lwjgl-stb`, `lwjgl-nfd` all publish
     * `natives-windows`, `-windows-arm64`, `-linux`, `-linux-arm64`, `-macos` and `-macos-arm64`), so
     * no platform of the set is a combination LWJGL has no artifact for.
     *
     * All six sit on the one launch class path because LWJGL 3.4 lays its natives out per platform
     * inside the jar (`linux/arm64/org/lwjgl/liblwjgl.so`, `macos/arm64/org/lwjgl/liblwjgl.dylib`,
     * ...): the loader looks up the directory of the machine the game runs on and never sees the
     * other five, which is why the zip can carry all of them instead of one.
     */
    val distributionNativePlatforms = listOf(
        "natives-windows", "natives-windows-arm64",
        "natives-linux", "natives-linux-arm64",
        "natives-macos", "natives-macos-arm64",
    )

    /** The coordinate of one module's natives for one platform. */
    fun natives(version: String, module: String, classifier: String): String =
        "org.lwjgl:$module:$version:$classifier"
}
