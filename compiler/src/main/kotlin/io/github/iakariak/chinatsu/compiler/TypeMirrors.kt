package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.squareup.kotlinpoet.ClassName


object TypeMirrors {
    val FabricLoader = ClassName("net.fabricmc.loader.api", "FabricLoader")
    val ModInitializer = ClassName("net.fabricmc.api", "ModInitializer")
    val EnvType = ClassName("net.fabricmc.api", "EnvType")
    val Configs = ClassName("io.github.iakariak.chinatsu.config", "Configs")
    val Codec = ClassName("com.mojang.serialization", "Codec")
    val StreamCodec = ClassName("net.minecraft.network.codec", "StreamCodec")
    val FriendlyByteBuf = ClassName("net.minecraft.network", "FriendlyByteBuf")
    val ByteBufCodecs = ClassName("net.minecraft.network.codec", "ByteBufCodecs")
    val Event = ClassName("net.fabricmc.fabric.api.event", "Event")
    val RecordCodecBuilder = ClassName("com.mojang.serialization.codecs", "RecordCodecBuilder")
}

context(env: ProcessEnv)
fun ClassName.resolve() = env.resolver.getClassDeclarationByName(canonicalName)!!