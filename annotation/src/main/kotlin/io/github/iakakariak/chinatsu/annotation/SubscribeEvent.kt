package io.github.iakakariak.chinatsu.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent(
    val registry: String,
    val side: SideType = SideType.Common
)