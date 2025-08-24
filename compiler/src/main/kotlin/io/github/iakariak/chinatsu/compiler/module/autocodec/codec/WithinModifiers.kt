package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors

private class WithinNumberModifier<N : Number>(
    val startInclusive: N,
    val endInclusive: N
) : CodecModifier {
    context(info: CodecPropertyInfo?)
    override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
        "%L.validate(%T.checkRange(%L, %L))",
        codecCalling,
        TypeMirrors.Codec,
        startInclusive,
        endInclusive,
    )
}

internal class WithinIntModifier(range: WithinInt) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinFloatModifier(range: WithinFloat) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinDoubleModifier(range: WithinDouble) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinLongModifier(range: WithinLong) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

