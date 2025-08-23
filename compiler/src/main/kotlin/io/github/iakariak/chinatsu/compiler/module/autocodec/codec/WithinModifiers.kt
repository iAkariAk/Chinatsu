package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors

internal class WithinIntModifier(range: WithinInt) : CodecModifier {
    override val descriptorBlockTransformer = object : CodecModifier.DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.intRange(%L, %L)",
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
}

internal class WithinFloatModifier(range: WithinFloat) : CodecModifier {
    override val descriptorBlockTransformer = object : CodecModifier.DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.floatRange(%L, %L)",
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
}

internal class WithinDoubleModifier(range: WithinDouble) : CodecModifier {
    override val descriptorBlockTransformer = object : CodecModifier.DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.doubleRange(%L, %L)",
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
}


internal class WithinLongModifier(range: WithinLong) : CodecModifier {
    override val descriptorBlockTransformer = object : CodecModifier.DescriptorBlockTransformer {
        context(info: CodecPropertyInfo)
        override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
            "%T.LONG.validate(%T.checkRange(%L, %L))",
            TypeMirrors.Codec,
            TypeMirrors.Codec,
            range.startInclusive,
            range.endInclusive,
        )
    }
}

