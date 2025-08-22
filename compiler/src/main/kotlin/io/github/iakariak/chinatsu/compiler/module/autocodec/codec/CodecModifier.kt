package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock

internal interface CodecModifier {
    val encodeBlockTransformer: EncodeBlockTransformer
    val decodeBlockTransformer: DecodeBlockTransformer

    companion object Empty : CodecModifier {
        override val encodeBlockTransformer = object : EncodeBlockTransformer {}
        override val decodeBlockTransformer = object : DecodeBlockTransformer {}
    }

     interface EncodeBlockTransformer {
        context(info: CodecPropertyInfo)
        fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

        context(info: CodecPropertyInfo)
        fun transformKeyOf(keyOf: CodeBlock): CodeBlock = keyOf

        context(info: CodecPropertyInfo)
        fun transformArg(arg: CodeBlock): CodeBlock = arg // comap
    }

    interface DecodeBlockTransformer {
        context(info: CodecPropertyInfo)
        fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling

        context(info: CodecPropertyInfo)
        fun transformKeyOf(keyOf: CodeBlock): CodeBlock = keyOf

        context(info: CodecPropertyInfo)
        fun transformResult(result: CodeBlock): CodeBlock = result // map
    }

}


internal fun List<CodecModifier>.composed(): CodecModifier = object : CodecModifier {
    override val encodeBlockTransformer = object : CodecModifier.EncodeBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) =
            fold(codecCalling) { ace, e -> e.encodeBlockTransformer.transformCodecCalling(ace) }

        context(info: CodecPropertyInfo)
        override fun transformKeyOf(keyOf: CodeBlock)=
            fold(keyOf) { ace, e -> e.encodeBlockTransformer.transformKeyOf(ace) }

        context(info: CodecPropertyInfo)
        override fun transformArg(arg: CodeBlock) =
            fold(arg) { ace, e -> e.encodeBlockTransformer.transformArg(ace) }

    }
    override val decodeBlockTransformer = object : CodecModifier.DecodeBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) =
            fold(codecCalling) { ace, e -> e.decodeBlockTransformer.transformCodecCalling(ace) }

        context(info: CodecPropertyInfo)
        override fun transformKeyOf(keyOf: CodeBlock)=
            fold(keyOf) { ace, e -> e.decodeBlockTransformer.transformKeyOf(ace) }

        context(info: CodecPropertyInfo)
        override fun transformResult(result: CodeBlock) =
            fold(result) { ace, e -> e.decodeBlockTransformer.transformResult(ace) }
    }
}