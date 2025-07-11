plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    implementation(project(":annotation"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}


kotlin {
    compilerOptions {
        optIn = listOf(
            "com.google.devtools.ksp.KspExperimental"
        )
    }
}