import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.loom)
    alias(libs.plugins.ksp)
    alias(libs.plugins.shadow)
    id("maven-publish")
}


val modId = rootProject.property("mod_id") as String
val kotlinLoaderVersion = rootProject.property("kotlin_loader_version") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}


val shadowInclude: Configuration by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    shadowInclude(libs.kotlinx.serialization.json)
    api(project(":annotation"))
    shadowInclude(project(":annotation"))
    ksp(project(":compiler"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version")
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}


tasks {
    shadowJar {
        isZip64 = true
        configurations = listOf(shadowInclude)
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

    jar {
        dependsOn(shadowJar)
        onlyIf { false }
    }

    processResources {
        val properties = HashMap<String, Any>()
        properties["modId"] = modId
        properties["version"] = version
        properties["fabricKotlinVersion"] = ">=$kotlinLoaderVersion"

        properties.forEach { (a, b) -> inputs.property(a, b) }

        filesNotMatching(
            listOf(
                "**/*.java",
                "**/*.kt",
                "**/sounds.json",
                "**/lang/*.json",
                "**/.cache/*",
                "**/*.accesswidener",
                "**/*.nbt",
                "**/*.png",
                "**/*.ogg",
                "**/*.mixins.json"
            )
        ) {
            expand(properties)
        }
    }
}