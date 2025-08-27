@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        google()
    }
}

includeBuild("build-logic")
include(":annotation")
include(":compiler")
include(":core")
include(":example")
