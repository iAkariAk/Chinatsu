package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors

internal class WithinIntModifier(range: WithinInt) : StreamCodecModifier {
    override val encodeBlockTransformer = object : StreamCodecModifier.EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.fromCodec(%T.intRange(%L, %L))",
            TypeMirrors.ByteBufCodecs,
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
    override val decodeBlockTransformer = object : StreamCodecModifier.DecodeBlockTransformer {}
}

internal class WithinFloatModifier(range: WithinFloat) : StreamCodecModifier {
    override val encodeBlockTransformer = object : StreamCodecModifier.EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.fromCodec(%T.floatRange(%L, %L))",
            TypeMirrors.ByteBufCodecs,
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
    override val decodeBlockTransformer = object : StreamCodecModifier.DecodeBlockTransformer {}
}

internal class WithinDoubleModifier(range: WithinDouble) : StreamCodecModifier {
    override val encodeBlockTransformer = object : StreamCodecModifier.EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.fromCodec(%T.doubleRange(%L, %L))",
            TypeMirrors.ByteBufCodecs,
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
    override val decodeBlockTransformer = object : StreamCodecModifier.DecodeBlockTransformer {}
}
internal class WithinLongModifier(withinInt: WithinLong) : StreamCodecModifier {
    override val encodeBlockTransformer = object : StreamCodecModifier.EncodeBlockTransformer {
        context(info: StreamCodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.fromCodec(%T.LONG.validate(%T.checkRange(%L, %L)))",
            TypeMirrors.ByteBufCodecs,
            TypeMirrors.Codec,
            TypeMirrors.Codec,
            withinInt.startInclusive,
            withinInt.endInclusive,
        )
    }
    override val decodeBlockTransformer = object : StreamCodecModifier.DecodeBlockTransformer {}
}