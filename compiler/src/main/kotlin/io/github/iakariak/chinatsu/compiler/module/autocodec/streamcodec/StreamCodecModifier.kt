package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.CodeBlock

internal interface StreamCodecModifier {
    val encodeBlockTransformer: EncodeBlockTransformer
    val decodeBlockTransformer: DecodeBlockTransformer

    companion object Empty : StreamCodecModifier {
        override val encodeBlockTransformer = object : EncodeBlockTransformer {}
        override val decodeBlockTransformer = object : DecodeBlockTransformer {}
    }

     interface EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

        context(info: StreamCodecPropertyInfo)
        fun transformArg(arg: CodeBlock): CodeBlock = arg // comap
    }

     interface DecodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

        context(info: StreamCodecPropertyInfo)
        fun transformResult(result: CodeBlock): CodeBlock = result
    }
}



internal fun List<StreamCodecModifier>.composed(): StreamCodecModifier = object : StreamCodecModifier {
    override val encodeBlockTransformer = object : StreamCodecModifier.EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) =
            fold(codecCalling) { ace, e -> e.encodeBlockTransformer.transformCodecCalling(ace) }

        context(info: StreamCodecPropertyInfo)
        override fun transformArg(arg: CodeBlock) =
            fold(arg) { ace, e -> e.encodeBlockTransformer.transformArg(ace) }

    }
    override val decodeBlockTransformer = object : StreamCodecModifier.DecodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) =
            fold(codecCalling) { ace, e -> e.decodeBlockTransformer.transformCodecCalling(ace) }

        context(info: StreamCodecPropertyInfo)
        override fun transformResult(result: CodeBlock) =
            fold(result) { ace, e -> e.decodeBlockTransformer.transformResult(ace) }
    }
}