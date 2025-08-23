package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock

internal interface CodecModifier {
    val descriptorBlockTransformer: DescriptorBlockTransformer

    companion object Empty : CodecModifier {
        override val descriptorBlockTransformer = object : DescriptorBlockTransformer {}
    }

    interface DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        fun transformCodecCalling(codecCalling: CodeBlock): CodeBlock = codecCalling


        /**
         * contravariance arg value from `forGetting`
         */
        context(info: CodecPropertyInfo)
        fun transformGetting(arg: CodeBlock): CodeBlock = arg

        /**
         * covariance arg value from curry block to construct object
         */
        context(info: CodecPropertyInfo)
        fun transformConstructor(arg: CodeBlock): CodeBlock = arg
    }
}


internal fun List<CodecModifier>.composed(): CodecModifier = object : CodecModifier {
    override val descriptorBlockTransformer = object : CodecModifier.DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) =
            fold(codecCalling) { ace, e -> e.descriptorBlockTransformer.transformCodecCalling(ace) }

        context(info: CodecPropertyInfo)
        override fun transformGetting(arg: CodeBlock) =
            fold(arg) { ace, e -> e.descriptorBlockTransformer.transformGetting(ace) }

        context(info: CodecPropertyInfo)
        override fun transformConstructor(arg: CodeBlock) =
            fold(arg) { ace, e -> e.descriptorBlockTransformer.transformConstructor(ace) }

    }
}