pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Standalone project: the provider (which publishes the real Fabric Loader) and the example
        // mod are consumed as Maven artifacts published by their own builds (publishToMavenLocal), not
        // as Gradle subprojects. The API is not resolved here at all: it is a *mod*, so this build
        // takes its jars as files of `../fabric-api`'s own build output and a repository would only
        // be able to hand `javac` classes the loader never sees (see the comment on `apiModJars` in
        // build.gradle.kts).
        mavenLocal()
        mavenCentral()
        // The API modules resolve Fabric Loader (and Mixin) from Fabric's maven (neither is on Maven
        // Central).
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

rootProject.name = "PvZ-Portable"
