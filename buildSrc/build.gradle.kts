plugins {
    // The Kotlin DSL plugin for build logic: the Gradle API and the embedded Kotlin, nothing else.
    // Everything this build needs at *build* time lives here (including the Groovy JSON writer the
    // version json is written with, which is part of the Gradle API); nothing of it reaches the
    // distribution.
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}
