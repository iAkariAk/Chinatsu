package io.github.iakakariak.chinatsu.annotation

import org.intellij.lang.annotations.Language

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Listen(
    @Language("kotlin", prefix = "fun main() { val x = ", suffix = "}")
    val registry: String,
    val side: SideType = SideType.Common
)