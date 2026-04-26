@file:Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.10.0")
}

rootProject.name = "GanvoLite"
include(":app")
include(":innertube")