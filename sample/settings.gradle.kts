pluginManagement {
    // In your mod the kit is a submodule: includeBuild("kit/consumer-plugin")
    includeBuild("../consumer-plugin")
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

stonecutter {
    create(rootProject) {
        versions("26.1.2", "26.2", "26.3")
        vcsVersion = "26.2"
    }
}

// In your mod: includeBuild("kit")
includeBuild("..") {
    dependencySubstitution {
        for (mc in listOf("26.1.2", "26.2", "26.3")) substitute(module("dev.jamie:mc-ui-kit-$mc")).using(project(":$mc"))
    }
}

rootProject.name = "uikit-sample"
