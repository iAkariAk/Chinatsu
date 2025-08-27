plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.loom)
    alias(libs.plugins.ksp)
    alias(libs.plugins.shadow)
    id("chinatsu")
    id("maven-publish")
}


base {
    archivesName.set(project.property("archives_base_name") as String)
}

dependencies {
    shadowInclude(libs.kotlinx.serialization.json)
    api(project(":annotation"))
    shadowInclude(project(":annotation"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}
