package cn.enaium.fabricpvz.build

import org.gradle.jvm.application.tasks.CreateStartScripts

/**
 * Two things cannot come from the application's default JVM arguments and go into the generated
 * start scripts' own `DEFAULT_JVM_OPTS` assignment instead. The scripts stay generated - nothing
 * else about them is hand-written - and this only appends to that one assignment:
 *
 *   * **the game directory.** Fabric Loader reads `mods/` from the game directory, and an unpacked
 *     distribution has to find it next to `bin/` (the unpacked root) no matter where the process is
 *     started from. Only the script knows where it lives, and Gradle's template already resolves
 *     that into APP_HOME from the script's own location, so the script passes
 *     `-Dpvz.gameDir=$APP_HOME` (the `.bat`: `%APP_HOME%`). It is a *default*: the script expands
 *     DEFAULT_JVM_OPTS before the user's JAVA_OPTS/PVZ_PORTABLE_OPTS, and a later `-D` of the same
 *     name wins in the JVM, so a user-supplied `-Dpvz.gameDir` overrides it.
 *   * **`-XstartOnFirstThread`**, which the unix script has to pass on macOS and must not pass
 *     anywhere else (SDL on macOS needs the JVM's main thread to own the first thread, and that
 *     switch is a *launcher* option only macOS's `java` consumes). It goes in behind a platform
 *     test, so the zip's scripts work on the machine they are unpacked on - and the zip itself stays
 *     one zip for every platform, because the test runs on that machine, not when the zip is built.
 *     The Windows script needs none of it: it never runs on macOS, so it only gets the game
 *     directory.
 */
fun CreateStartScripts.pvzLaunchDefaults() {
    doLast {
        val unixScript = unixScriptFile.get().asFile
        val unixAssignment = unixScript.readLines().first { it.startsWith("DEFAULT_JVM_OPTS=") }
        unixScript.writeText(
            unixScript.readText().replace(
                unixAssignment,
                unixAssignment + "\n" +
                    "# The launcher's game directory is the unpacked root next to bin/ (where Fabric Loader\n" +
                    "# reads mods/), so mods/ is found however this script is started. It is a default: a\n" +
                    "# user's -Dpvz.gameDir in JAVA_OPTS/PVZ_PORTABLE_OPTS comes later and wins.\n" +
                    "DEFAULT_JVM_OPTS=\"\$DEFAULT_JVM_OPTS \\\"-Dpvz.gameDir=\$APP_HOME\\\"\"\n" +
                    "# SDL on macOS needs the JVM's main thread to own the first thread; the switch is\n" +
                    "# a launcher option only macOS's java knows, so it is added on the machine the\n" +
                    "# script runs on and never on the machine that built the zip.\n" +
                    "if [ \"$(uname -s)\" = \"Darwin\" ]; then\n" +
                    "    DEFAULT_JVM_OPTS=\"\$DEFAULT_JVM_OPTS -XstartOnFirstThread\"\n" +
                    "fi",
            ),
        )

        val windowsScript = windowsScriptFile.get().asFile
        val windowsAssignment = windowsScript.readLines().first { it.startsWith("set DEFAULT_JVM_OPTS=") }
        windowsScript.writeText(
            windowsScript.readText().replace(
                windowsAssignment,
                windowsAssignment + "\r\n" +
                    "@rem The launcher's game directory is the unpacked root next to bin/ (where Fabric Loader\r\n" +
                    "@rem reads mods), so mods/ is found however this script is started. It is a default: a\r\n" +
                    "@rem user's -Dpvz.gameDir in JAVA_OPTS/PVZ_PORTABLE_OPTS comes later and wins.\r\n" +
                    "set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% \"-Dpvz.gameDir=%APP_HOME%\"",
            ),
        )
    }
}
