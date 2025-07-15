package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver


@Suppress("PropertyName")
class TypeMirrors(private val resolver: Resolver) {
    private fun get(qualifiedName: String) = resolver.getClassDeclarationByName(qualifiedName)!!

    val FabricLoader = get("net.fabricmc.loader.api.FabricLoader")
    val ModInitializer = get("net.fabricmc.api.ModInitializer")
    val EnvType = get("net.fabricmc.api.EnvType")
    val Configs = get("io.github.iakariak.chinatsu.config.Configs")
    val Codec = get("com.mojang.serialization.Codec")
    val StreamCodec = get("net.minecraft.network.codec.StreamCodec")
    val FriendlyByteBuf = get("net.minecraft.network.FriendlyByteBuf")
    val ByteBufCodecs = get("net.minecraft.network.codec.ByteBufCodecs")
    val Event = get("net.fabricmc.fabric.api.event.Event")
    val RecordCodecBuilder = get("com.mojang.serialization.codecs.RecordCodecBuilder")
}
