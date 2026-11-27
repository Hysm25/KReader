rootProject.name = "KReader"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
}

// Application Module
include(":composeApp")

// Core Modules
include(":core:model")
include(":core:database")
include(":core:engine-api")

// Engine Adapters
include(":engine:epub-adapter")
include(":engine:cbz-adapter")

// Features
include(":feature:library")
include(":feature:reader")
