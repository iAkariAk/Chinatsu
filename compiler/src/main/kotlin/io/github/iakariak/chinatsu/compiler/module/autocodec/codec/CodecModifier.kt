package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.compiler.module.autocodec.CodecCalling

internal interface CodecModifier {
    fun transformCodecCalling(codecCalling: CodecCalling): CodecCalling = codecCalling

    /**
     * Transform the arg from `forGetting` that is equivalent to encode
     */
    context(info: CodecPropertyInfo)
    fun transformGetting(arg: CodeBlock): CodeBlock = arg

    /**
     * Transform arg value from curry block to construct object that is equivalent to decode
     */
    context(info: CodecPropertyInfo)
    fun transformConstructor(arg: CodeBlock): CodeBlock = arg

    companion object Empty : CodecModifier
}


internal fun Iterable<CodecModifier>.composed(): CodecModifier = object : CodecModifier {
    override fun transformCodecCalling(codecCalling: CodecCalling) =
        fold(codecCalling) { ace, e -> e.transformCodecCalling(ace) }

    context(info: CodecPropertyInfo)
    override fun transformGetting(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformGetting(ace) }

    context(info: CodecPropertyInfo)
    override fun transformConstructor(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformConstructor(ace) }
}