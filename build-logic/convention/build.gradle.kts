plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()

    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
    }
}

kotlin {
    compilerOptions {
        // Refer to https://kotlinlang.org/docs/whatsnew22.html
        freeCompilerArgs = listOf(
            "-Xcontext-parameters",
            "-Xcontext-sensitive-resolution",
            "-Xnested-type-aliases",
        )
    }
}

gradlePlugin {
    plugins {
        register("chinatsu") {
            id = "chinatsu"
            implementationClass = "ChinatsuPlugin"
        }
    }
}

dependencies {
    compileOnly(libs.loom.plugin)
    compileOnly(libs.shadow.plugin)
}
