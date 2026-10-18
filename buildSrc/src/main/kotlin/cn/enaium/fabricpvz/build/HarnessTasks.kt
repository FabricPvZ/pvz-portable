package cn.enaium.fabricpvz.build

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers one of the game's driver tasks: a [JavaExec] that runs a test-fixture harness
 * (`cn.enaium.fabricpvz.portable.test.*`) on the test runtime class path, in the development game
 * directory (`run/`), with the window and the audio driver usable in a headless-ish session.
 *
 * Every driver task is that same launch, so the launch is written once here and each task names only
 * what makes it different: its main class, its description and the system properties it forwards.
 * The forwarding rule is the workspace's own - a Gradle property (`-Ppvz.…`) wins over a `-D` - and
 * a switch that is not passed is not set at all (the harness picks its own default).
 *
 * `SDL_AUDIODRIVER=dummy` is part of the launch: the drivers run without an audio device, and sounds
 * that never finish would stall the cutscenes that wait for them (the level intro) forever.
 * `-XstartOnFirstThread` is added on macOS only - SDL needs the JVM's main thread to own the first
 * thread, and no other platform's launcher knows that switch.
 */
fun Project.pvzHarness(
    name: String,
    mainClass: String,
    description: String,
    switches: List<String>,
): TaskProvider<JavaExec> {
    val gameDir = layout.projectDirectory.dir("run").asFile
    val testRuntimeClasspath = extensions.getByType(SourceSetContainer::class.java)
        .getByName("test").runtimeClasspath
    val mac = System.getProperty("os.name").lowercase().contains("mac")
    val audioDriver = System.getenv("SDL_AUDIODRIVER") ?: "dummy"
    return tasks.register(name, JavaExec::class.java) {
        group = "verification"
        this.description = description
        this.mainClass.set(mainClass)
        classpath = testRuntimeClasspath
        workingDir = gameDir
        doFirst { workingDir.mkdirs() }
        jvmArgs("-Dpvz.gameDir=${gameDir.absolutePath}")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        environment("SDL_AUDIODRIVER", audioDriver)
        for (switch in switches) {
            val value = providers.gradleProperty(switch).orNull ?: System.getProperty(switch)
            if (value != null) {
                systemProperty(switch, value)
            }
        }
        if (mac) {
            jvmArgs("-XstartOnFirstThread")
        }
    }
}
