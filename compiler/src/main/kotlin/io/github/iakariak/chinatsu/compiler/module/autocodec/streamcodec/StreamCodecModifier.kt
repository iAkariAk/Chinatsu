package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.compiler.module.autocodec.CodecCalling

internal interface StreamCodecModifier {
    fun transformCodecCalling(codecCalling: CodecCalling): CodecCalling = codecCalling

    /**
     * Transform the arg from `encode`
     */
    context(info: StreamCodecPropertyInfo)
    fun transformArg(arg: CodeBlock): CodeBlock = arg

    /**
     * A functor provide an ability to be translated by other modifier
     */
    context(info: StreamCodecPropertyInfo)
    fun transformSelfArg(arg: CodeBlock, transform: (CodeBlock) -> CodeBlock): CodeBlock = arg

    /**
     * Transform the result from `decode`
     */
    context(info: StreamCodecPropertyInfo)
    fun transformResult(result: CodeBlock): CodeBlock = result

    /**
     * A functor provide an ability to be translated by other modifier
     */
    context(info: StreamCodecPropertyInfo)
    fun transformSelfResult(result: CodeBlock, transform: (CodeBlock) -> CodeBlock): CodeBlock = result

}

internal fun Iterable<StreamCodecModifier>.composed(): StreamCodecModifier = object : StreamCodecModifier {
    override fun transformCodecCalling(codecCalling: CodecCalling) =
        fold(codecCalling) { ace, e -> e.transformCodecCalling(ace) }

    context(info: StreamCodecPropertyInfo)
    override fun transformArg(arg: CodeBlock) =
        fold(arg) { ace, e -> e.transformArg(ace) }

    context(info: StreamCodecPropertyInfo)
    override fun transformSelfArg(arg: CodeBlock, transform: (CodeBlock) -> CodeBlock) =
        fold(arg) { ace, e -> e.transformSelfArg(ace, transform) }

    context(info: StreamCodecPropertyInfo)
    override fun transformResult(result: CodeBlock) =
        fold(result) { ace, e -> e.transformResult(ace) }

    context(info: StreamCodecPropertyInfo)
    override fun transformSelfResult(result: CodeBlock, transform: (CodeBlock) -> CodeBlock) =
        fold(result) { ace, e -> e.transformSelfResult(ace, transform) }

}

