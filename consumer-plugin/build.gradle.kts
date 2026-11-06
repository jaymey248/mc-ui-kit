plugins {
    `kotlin-dsl`
}

group = "dev.jamie"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm-commons:9.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("uiKitConsumer") {
            id = "dev.jamie.uikit.consumer"
            implementationClass = "dev.jamie.uikit.gradle.UiKitPlugin"
        }
    }
}
