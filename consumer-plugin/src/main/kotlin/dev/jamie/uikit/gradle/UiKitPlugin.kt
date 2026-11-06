package dev.jamie.uikit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.jvm.tasks.ProcessResources

/** What the mod tells the kit: `uiKit { modId = "mymod"; relocateTo = "dev.me.mymod.uikit"; minecraft = sc.current.version }`. */
abstract class UiKitExtension {
    /** The mod's id. The kit's assets move under `assets/<modId>/`; give `Kit.setup` the same id. */
    abstract val modId: Property<String>

    /** The package this mod's copy of the kit is renamed to in the jar. Must be unique to the mod. */
    abstract val relocateTo: Property<String>

    /** The Minecraft version of this Stonecutter node. */
    abstract val minecraft: Property<String>
}

/**
 * Puts the kit inside a mod. The mod compiles against the kit's real package (`dev.jamie.uikit`), and:
 *
 * - `processResources` merges the kit's classes and its assets (moved to the mod's namespace) into the
 *   mod's own output, so a dev client sees one mod and the kit's fonts and shaders are found;
 * - `jar` then renames the kit's package to [UiKitExtension.relocateTo] in every class of the jar, the
 *   mod's own included.
 *
 * Each jar so carries a private copy that no other mod's copy can clash with, and no library to install.
 */
class UiKitPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val ext = extensions.create("uiKit", UiKitExtension::class.java)
        val coordinates = ext.minecraft.map { "dev.jamie:mc-ui-kit-$it" }

        val kit: Configuration = configurations.create("uiKit") {
            isCanBeConsumed = false
            isTransitive = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        }
        dependencies.addProvider(kit.name, coordinates)

        // Compile against it here; at run time it is part of the mod's own output
        pluginManager.withPlugin("java") {
            dependencies.addProvider<String, ExternalModuleDependency>("compileOnly", coordinates) { isTransitive = false }

            tasks.named("processResources", ProcessResources::class.java) {
                dependsOn(kit)
                inputs.property("uiKitModId", ext.modId)
                from(kit.elements.map { files -> files.map { zipTree(it) } }) {
                    exclude("META-INF/**", "assets/**")
                }
                from(kit.elements.map { files -> files.map { zipTree(it) } }) {
                    include("assets/uikit/**")
                    val modId = ext.modId
                    eachFile { path = path.replaceFirst("assets/uikit/", "assets/${modId.get()}/") }
                    filesMatching("**/shaders/**") {
                        filter { line: String -> line.replace("<uikit:", "<${modId.get()}:") }
                    }
                    includeEmptyDirs = false
                }
            }

            tasks.named("jar", Jar::class.java) {
                inputs.property("uiKitRelocateTo", ext.relocateTo)
                val target = ext.relocateTo
                doLast { Relocator.relocate(archiveFile.get().asFile, KIT_PACKAGE, target.get()) }
            }

            // A GPL mod's sources jar has to carry the kit's source too
            val kitSources: Configuration = configurations.create("uiKitSources") {
                isCanBeConsumed = false
                isTransitive = false
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.SOURCES))
            }
            dependencies.addProvider(kitSources.name, coordinates)
            tasks.matching { it.name == "sourcesJar" }.configureEach {
                val jar = this as Jar
                jar.dependsOn(kitSources)
                jar.from(kitSources.elements.map { files -> files.map { zipTree(it) } }) {
                    into("uikit-source")
                    exclude("META-INF/**")
                }
            }
        }
    }

    private companion object {
        const val KIT_PACKAGE = "dev.jamie.uikit"
    }
}
