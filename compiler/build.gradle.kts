plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    implementation(project(":annotation"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}