pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Standalone project: every sibling artifact - the provider (which publishes the real Fabric
        // Loader), the API's feature modules and the example mod - is consumed as a Maven artifact its
        // own build has published with `publishToMavenLocal`, never as a Gradle subproject and never
        // as a file of a sibling checkout (see `apiVersion` in build.gradle.kts).
        mavenLocal()
        mavenCentral()
        // Fabric Loader (and Mixin) also resolve from Fabric's maven.
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

rootProject.name = "PvZ-Portable"
