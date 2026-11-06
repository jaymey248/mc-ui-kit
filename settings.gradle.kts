pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
}

// Every supported Minecraft version is a Stonecutter node. To add one: add it here, add
// versions/<ver>/gradle.properties, then fix what stops compiling. Version differences belong in
// compat/, in //? blocks. The source tree in git is always the vcsVersion's.
stonecutter {
    create(rootProject) {
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "mc-ui-kit"
