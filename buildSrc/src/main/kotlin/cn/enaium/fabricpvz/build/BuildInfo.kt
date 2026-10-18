package cn.enaium.fabricpvz.build

import org.gradle.api.Project

/**
 * The build identity the game reports at startup (`SexyApp` logs `Product`, `Version`, `Build Num` and
 * `Commit Date`, and `-version` prints the same block).
 *
 * <p>The first version of the port left all of them empty - a build number of zero, an empty commit
 * date and an empty product version - which is what the log showed. The values come from the working
 * copy the build runs in: the commit count is the build number (it only grows, which is what a build
 * number is for), and the commit's own date is the commit date (the date of the code, not of the
 * build). A tree without git, or without commits, simply states nothing - the fields are then empty
 * rather than wrong.
 */
object BuildInfo {

    /** The keys of the resource, also what the build script writes with. */
    object Keys {
        const val VERSION = "version"
        const val BUILD_NUMBER = "buildNumber"
        const val COMMIT_DATE = "commitDate"
    }


    /** The resource the game reads them from, at the root of its jar. */
    const val RESOURCE = "build-info.properties"


    /** The outputs of `git <arguments>` in this project's directory, or null when git cannot answer. */
    private fun Project.git(vararg arguments: String): String? {
        return try {
            val output = providers.exec {
                commandLine(listOf("git") + arguments)
                workingDir = projectDir
            }
            output.standardOutput.asText.get().trim().ifEmpty { null }
        } catch (exception: Exception) {
            // No git, no repository, or a detached environment: the game states nothing instead.
            null
        }
    }

    /** How many commits the working copy has: the build number, or 0 when git cannot answer. */
    fun buildNumber(project: Project): Int =
        with(project) { git("rev-list", "--count", "HEAD") }?.toIntOrNull() ?: 0

    /** The date of the commit the build is from (ISO-8601), or an empty string when git cannot answer. */
    fun commitDate(project: Project): String = with(project) { git("log", "-1", "--format=%cI") } ?: ""
}
