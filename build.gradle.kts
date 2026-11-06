import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // 26.x ships unobfuscated: the fully-qualified id is Loom's no-remap plugin
    id("net.fabricmc.fabric-loom") version "1.17.20"
    kotlin("jvm") version "2.4.10"
}

fun prop(key: String): String = property(key) as String

val mcVersion = sc.current.version
val javaVersion = 25

// Minecraft 26.3's new shader compiler needs different GLSL, so each generation has its own files
val shaderSet = if (mcVersion.split('.')[1].toInt() >= 3) "26.3" else "pre-26.3"
sourceSets.main { resources.srcDir(rootProject.file("src/shaders/$shaderSet")) }

group = "dev.jamie"
version = "${prop("kit.version")}+$mcVersion"
base.archivesName.set("mc-ui-kit")

repositories {
    mavenCentral()
}

// The kit is no mod: it ends up inside a mod's jar and uses what that mod has, and the consumer plugin takes it
// without these. They are implementation, not compileOnly, because Loom applies Fabric API's transitive access
// wideners only from a real dependency, and GuiElements needs them (the scissor stack, the GUI render state).
dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar() // a GPL mod built on the kit ships these in its own sources jar
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        freeCompilerArgs.addAll("-jvm-default=enable")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

// A bare client with no mod of its own, to try finished jars together: put them in run/<version>/mods.
// -Duikit.vmargs="-Dsample.dev=check" passes flags on to them.
loom {
    runConfigs.all {
        runDir = "../../run/$mcVersion"
        System.getProperty("uikit.vmargs")?.split(' ')?.forEach { vmArg(it) }
        programArgs("--width", "1280", "--height", "720")
    }
}
