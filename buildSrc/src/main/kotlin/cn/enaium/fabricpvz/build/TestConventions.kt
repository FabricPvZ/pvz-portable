package cn.enaium.fabricpvz.build

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * The conventions every test task of this project has, in one place:
 *
 *  * JUnit 5, with the full exception format a failing ported test needs;
 *  * the **mods** the loader end-to-end test loads from a real `mods/` directory: [modsDir]
 *    (`build/testmods`, filled by `testModsJar`) is passed in as `pvz.test.modsDir`, because the test
 *    has to find the API where a player's installation keeps it and the test class path carries no
 *    API at all;
 *  * the **release zip** the distribution test asserts (the packaged native libopenmpt libraries of
 *    every platform, by entry name and sha256): `distZip` is what produces it, so the tests depend on
 *    it and the test task passes the archive's path in as `pvz.test.distribution` (the test does not
 *    guess the version's file name);
 *  * **LWJGL's JNI**: JDK 25 warns unless native access is enabled explicitly, and SDL on macOS needs
 *    the first thread;
 *  * **SDL's dummy video and audio drivers**: the tests run headless-ish, and sounds that never
 *    finish would stall the cutscenes that wait for them.
 */
fun Project.pvzTestConventions(modsDir: Provider<Directory>) {
    // Both are read lazily, when the test task's command line is built - i.e. after `distZip` has
    // run - so that merely listing or configuring the test task does not query `distZip`'s output.
    val distribution = tasks.named("distZip", Zip::class.java)
        .flatMap { it.archiveFile }
        .map { it.asFile.absolutePath }
    val mac = System.getProperty("os.name").lowercase().contains("mac")
    val videoDriver = System.getenv("SDL_VIDEODRIVER") ?: "dummy"
    val audioDriver = System.getenv("SDL_AUDIODRIVER") ?: "dummy"
    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        dependsOn(tasks.named("testModsJar"))
        systemProperty("pvz.test.modsDir", modsDir.get().asFile.absolutePath)
        dependsOn(tasks.named("distZip"))
        systemProperty("pvz.test.distribution", distribution)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        if (mac) {
            jvmArgs("-XstartOnFirstThread")
        }
        environment("SDL_VIDEODRIVER", videoDriver)
        environment("SDL_AUDIODRIVER", audioDriver)
    }
}
