package cn.enaium.fabricpvz.build

import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider

/**
 * The development `run`: the game exactly as the distribution's `bin/pvz-portable` script launches it,
 * on the application's own class path - the game, the provider (which publishes the real Fabric Loader)
 * and their runtime dependencies; no mod is on it and none is added to it here.
 *
 * It starts in the development game directory, which is a real game directory: Fabric Loader reads
 * `mods/` from it ([gameDir] is passed as `-Dpvz.gameDir`, the property the provider prefers - the
 * launcher's `-gamedir` would reach the game's own command line parser, which exits on parameters it
 * does not know). `-Dfabric.development=true` reports a development environment and makes the loader
 * discover mods on the class path too, like the mods' own `./gradlew runClient` (`../loom/`).
 *
 * [natives] is the packaged native library stage that goes next to it (`run/lib/native/`), so a dev
 * run resolves libopenmpt exactly like an unpacked release does.
 */
fun Project.configureRunTask(gameDir: File, natives: TaskProvider<*>): TaskProvider<JavaExec> {
    val mac = System.getProperty("os.name").lowercase().contains("mac")
    return tasks.named("run", JavaExec::class.java) {
        workingDir = gameDir
        dependsOn(natives)
        // Create the working directory on demand (it holds userdata/, mods/ and, optionally, the
        // assets synced by :syncDevResources).
        doFirst { workingDir.mkdirs() }
        jvmArgs("-Dpvz.gameDir=${gameDir.absolutePath}")
        jvmArgs("-Dfabric.skipMcProvider=true")
        jvmArgs("-Dfabric.development=true")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        // SDL on macOS requires the JVM's main thread to own the first thread.
        if (mac) {
            jvmArgs("-XstartOnFirstThread")
        }
    }
}
