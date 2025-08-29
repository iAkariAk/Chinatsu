@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.ProviderFactory
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources


class Properties(providers: ProviderFactory) {
    val archivesName by providers.gradleProperty("archives_base_name")
    val minecraftVersion by providers.gradleProperty("minecraft_version")
    val loaderVersion by providers.gradleProperty("loader_version")
    val fabricVersion by providers.gradleProperty("fabric_version")
    val kotlinLoaderVersion by providers.gradleProperty("kotlin_loader_version")
}


class ChinatsuPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val properties = Properties(target.rootProject.providers)
            context(properties) {
                apply(plugin = "com.gradleup.shadow")
                apply(plugin = "fabric-loom")
                configLoom()
                configShadow()
                configJar()
            }
        }
    }

    context(props: Properties)
    fun Project.configJar() = afterEvaluate {
        tasks.named<Jar>("jar") {
            from("LICENSE") {
                rename { "${it}_${props.archivesName}" }
            }
        }

    }

    fun Project.configShadow() {
        val shadowInclude: Configuration = configurations.create("shadowInclude")

        afterEvaluate {
            tasks.shadowJar {
                isZip64 = true
                configurations.set(listOf(shadowInclude))
                dependencies {
                    // exclude libraries in Fabric Language Kotlin
                    exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
                    exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:.*"))
                    exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:.*"))
                    exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))

                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-cbor-jvm:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:atomicfu-jvm:.*"))
                    exclude(dependency("org.jetbrains.kotlinx:kotlinx-datetime-jvm:.*"))

                    exclude(dependency("com.google.guava:guava:.*"))

                    exclude(dependency("org.apache.commons:commons-lang3:.*"))
                    exclude(dependency("org.apache.httpcomponents:httpclient:.*"))
                    exclude(dependency("org.apache.httpcomponents:httpcore:.*"))
                    exclude(dependency("org.slf4j:slf4j-api:.*"))
                    exclude(dependency("org.slf4j:slf4j-simple:.*"))
                    exclude(dependency("org.ow2.asm:.*:.*"))
                }
            }
        }
    }

    context(props: Properties)
    fun Project.configLoom() {
        val loom = extensions.getByType<LoomGradleExtensionAPI>()
        dependencies {
            "minecraft"("com.mojang:minecraft:${props.minecraftVersion}")
            "mappings"(loom.officialMojangMappings())
            "modImplementation"("net.fabricmc:fabric-loader:${props.loaderVersion}")
            "modImplementation"("net.fabricmc:fabric-language-kotlin:${props.kotlinLoaderVersion}")
            "modImplementation"("net.fabricmc.fabric-api:fabric-api:${props.fabricVersion}")
        }
        val modVersion = version
        tasks.withType<ProcessResources> {
            inputs.property("version", modVersion)
            inputs.property("minecraft_version", props.minecraftVersion)
            inputs.property("loader_version", props.loaderVersion)
            filteringCharset = "UTF-8"

            filesMatching("fabric.mod.json") {
                expand(
                    "version" to modVersion,
                    "minecraft_version" to props.minecraftVersion,
                    "loader_version" to props.loaderVersion,
                    "kotlin_loader_version" to props.kotlinLoaderVersion
                )
                outputs.file(file)
            }
        }
    }
}
