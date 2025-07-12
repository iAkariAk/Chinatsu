package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver


@Suppress("PropertyName")
class TypeMirrors(private val resolver: Resolver) {
    private fun get(qualifiedName: String) = resolver.getClassDeclarationByName(qualifiedName)!!

    val FabricLoader = get("net.fabricmc.loader.api.FabricLoader")
    val ModInitializer = get("net.fabricmc.api.ModInitializer")
    val EnvType = get("net.fabricmc.api.EnvType")
    val Configs = get("io.github.iakakariak.chinatsu.config.Configs")
    val Codec = get("com.mojang.serialization.Codec")
    val StreamCodec = get("net.minecraft.network.codec.StreamCodec")
    val FriendlyByteBuf = get("net.minecraft.network.FriendlyByteBuf")
}
