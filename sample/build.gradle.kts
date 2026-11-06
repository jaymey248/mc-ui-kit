import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
    kotlin("jvm") version "2.4.10"
    id("dev.jamie.uikit.consumer")
}

fun prop(key: String): String = property(key) as String

val mcVersion = sc.current.version
val javaVersion = 25

version = "${prop("mod.version")}+$mcVersion"
base.archivesName.set("uikit-sample")

// The kit goes inside this jar, renamed to this mod's own package
uiKit {
    this.modId = prop("mod.id")
    relocateTo = "dev.jamie.uikitsample.uikit"
    minecraft = mcVersion
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        freeCompilerArgs.addAll("-jvm-default=enable")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

loom {
    runConfigs.all {
        runDir = "../../run/$mcVersion"
        System.getProperty("sample.dev")?.let { vmArg("-Dsample.dev=$it"); programArgs("--width", "1280", "--height", "720") }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to prop("mod.id"),
        "mc_range" to prop("minecraft_range"),
        "loader_version" to prop("loader_version"),
        "fabric_kotlin_version" to prop("fabric_kotlin_version"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
