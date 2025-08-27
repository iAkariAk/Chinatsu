package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.CodecPropertyInfo

internal interface StreamCodecModifier {
    context(info: CodecPropertyInfo)
    fun transformCodecType(type: CodeBlock): CodeBlock = type

    // The reason why info can be null:
    // Principally transformation (esp. expend) codec-calling not depend on info.
    // Since most fundamental codec-calling is already constructed in PropertyInfo using info.
    context(info: StreamCodecPropertyInfo?)
    fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

    /**
     * Transform the arg from `encode`
     */
    context(info: StreamCodecPropertyInfo)
    fun transformArg(arg: CodeBlock): CodeBlock = arg

    /**
     * Transform the result from `decode`
     */
    context(info: StreamCodecPropertyInfo)
    fun transformResult(result: CodeBlock): CodeBlock = result
}

internal fun List<StreamCodecModifier>.composed(): StreamCodecModifier = object : StreamCodecModifier {
    context(info: CodecPropertyInfo)
    override fun transformCodecType(type: CodeBlock) =
        fold(type) { ace, e -> e.transformCodecType(ace) }

    context(info: StreamCodecPropertyInfo?)
    override fun transformCodecCalling(codecCalling: CodeBlock) =
        fold(codecCalling) { ace, e -> e.transformCodecCalling(ace) }

    context(info: StreamCodecPropertyInfo)
    override fun transformArg(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformArg(ace) }

    context(info: StreamCodecPropertyInfo)
    override fun transformResult(result: CodeBlock) =
        fold(result) { ace, e -> e.transformResult(ace) }
}