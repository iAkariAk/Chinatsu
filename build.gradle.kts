import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.maven.publish) apply false
}


allprojects {
    version = project.property("mod_version") as String
    group = project.property("maven_group") as String
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
                    "-Xnested-type-aliases",
                )
            }
        }
    }


    if (project.name != "core") { // core need be published to modrinth and CF
        apply(plugin = "com.vanniktech.maven.publish.base")

        afterEvaluate {
            plugins.withId("signing") {
                extensions.configure<SigningExtension> {
                    useInMemoryPgpKeys(
                        findProperty("singing.key")?.toString(),
                        findProperty("signing.password")?.toString()
                    )
                }
            }
        }

        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()

            coordinates(
                groupId = project.group.toString(),
                artifactId = "chinatsu-${project.name}",
                version = project.version.toString()
            )

            pom {
                name.set("Chinatsu")
                description.set("A practiced Minecraft toolkit for fabric")
                inceptionYear.set("2025")
                url.set("https://github.com/iAkariAk/Chinatsu")

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("iakariak")
                        name.set("Akari")
                        url.set("https://github.com/iakariak/")
                    }
                }

                scm {
                    url.set("https://github.com/iAkariAk/Chinatsu")
                    connection.set("scm:git:git://github.com/iAkariAk/Chinatsu.git")
                    developerConnection.set("scm:git:ssh://git@github.com/iakariak/Chinatsu.git")
                }
            }
        }
    }
}
