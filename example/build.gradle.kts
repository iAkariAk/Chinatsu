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
    archivesName.set("${project.property("archives_base_name")}_example")
}

dependencies {
    shadowInclude(libs.kotlinx.serialization.json)
    ksp(project(":compiler"))
    val chinatsuJar = rootProject.projectDir
        .resolve("core/build/libs")
        .listFiles { it.name.endsWith("-all.jar") }?.firstOrNull()
    requireNotNull(chinatsuJar) {
        "Please run formerly shadowJar chinatus:core "
    }
    modImplementation(files(chinatsuJar))
}



ksp {
    arg("wrapNullableInCodec", "true")
}