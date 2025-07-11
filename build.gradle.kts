import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    plugins.withId("java") {
        configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(21)

            withSourcesJar()
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<KotlinJvmProjectExtension> {
            compilerOptions {
                // Refer to https://kotlinlang.org/docs/whatsnew22.html
                freeCompilerArgs = listOf(
                    "-Xcontext-parameters",
                    "-Xcontext-sensitive-resolution",
                    "-Xnested-type-aliases"
                )
            }
        }
    }
}
