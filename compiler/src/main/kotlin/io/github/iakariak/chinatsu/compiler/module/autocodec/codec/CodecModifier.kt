package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock

internal interface CodecModifier {
    context(info: CodecPropertyInfo)
    fun transformCodecType(type: CodeBlock): CodeBlock = type

    // The reason why info can be null:
    // Principally transformation (esp. expend) codec-calling not depend on info.
    // Since most fundamental codec-calling is already constructed in PropertyInfo using info.
    context(info: CodecPropertyInfo?)
    fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

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
}


internal fun List<CodecModifier>.composed(): CodecModifier = object : CodecModifier {
    context(info: CodecPropertyInfo)
    override fun transformCodecType(type: CodeBlock) =
        fold(type) { ace, e -> e.transformCodecCalling(ace) }

    context(info: CodecPropertyInfo?)
    override fun transformCodecCalling(codecCalling: CodeBlock) =
        fold(codecCalling) { ace, e -> e.transformCodecCalling(ace) }

    context(info: CodecPropertyInfo)
    override fun transformGetting(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformGetting(ace) }

    context(info: CodecPropertyInfo)
    override fun transformConstructor(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformConstructor(ace) }
}