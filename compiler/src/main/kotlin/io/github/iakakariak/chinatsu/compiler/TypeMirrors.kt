package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver

@Suppress("PropertyName")
class TypeMirrors(resolver: Resolver) {
    val FabricLoader = resolver.getClassDeclarationByName("net.fabricmc.loader.api.FabricLoader")!!
    val ModInitializer = resolver.getClassDeclarationByName("net.fabricmc.api.ModInitializer")!!
    val EnvType = resolver.getClassDeclarationByName("net.fabricmc.api.EnvType")!!
}
