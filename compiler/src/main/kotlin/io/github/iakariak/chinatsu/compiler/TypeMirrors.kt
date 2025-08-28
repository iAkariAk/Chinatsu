package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import java.util.*
import java.util.function.Function as JavaFunction


internal object TypeMirrors {
    val FabricLoader = ClassName("net.fabricmc.loader.api", "FabricLoader")
    val ModInitializer = ClassName("net.fabricmc.api", "ModInitializer")
    val EnvType = ClassName("net.fabricmc.api", "EnvType")
    val Configs = ClassName("io.github.iakariak.chinatsu.config", "Configs")
    val Codec = ClassName("com.mojang.serialization", "Codec")
    val StreamCodec = ClassName("net.minecraft.network.codec", "StreamCodec")
    val ByteBuf = ClassName("io.netty.buffer", "ByteBuf")
    val FriendlyByteBuf = ClassName("net.minecraft.network", "FriendlyByteBuf")
    val ByteBufCodecs = ClassName("net.minecraft.network.codec", "ByteBufCodecs")
    val Event = ClassName("net.fabricmc.fabric.api.event", "Event")
    val RecordCodecBuilder = ClassName("com.mojang.serialization.codecs", "RecordCodecBuilder")

    val DFEither = ClassName("com.mojang.datafixers.util", "Either")
    val DFPair = ClassName("com.mojang.datafixers.util", "Pair")
    val DFApp = ClassName("com.mojang.datafixers.kinds", "App")
    val DFK1 = ClassName("com.mojang.datafixers.kinds", "K1")
    val DFApplicative = ClassName("com.mojang.datafixers.kinds", "Applicative")
    val DFApplicativeMu = ClassName("com.mojang.datafixers.kinds", "Applicative", "Mu")
    val JFunction = JavaFunction::class.asClassName()
}

context(env: ProcessEnv)
internal fun ClassName.resolved() = env.resolver.getClassDeclarationByName(canonicalName)!!

context(env: ProcessEnv)
internal inline fun <reified T> KSType.isSubtypeOf(): Boolean {
    val anotherQN = env.resolver.getClassDeclarationByName<T>()!!.qualifiedName?.asString()
    return declaration.qualifiedName?.asString() == anotherQN
}

context(env: ProcessEnv)
internal val KSType.isOptional get() = isSubtypeOf<Optional<*>>()

context(env: ProcessEnv)
internal val KSType.isList get() = isSubtypeOf<List<*>>()

context(env: ProcessEnv)
internal val KSType.isMap get() = isSubtypeOf<Map<*, *>>()
