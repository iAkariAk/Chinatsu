package io.github.iakakariak.chinatsu.annotation

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FILE, AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION
)
annotation class Init(val side: SideType = SideType.Common)

enum class SideType {
    Server, Client, Common
}