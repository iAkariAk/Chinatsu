package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.CodecCalling

private class WithinNumberModifier<N : Number>(
    val startInclusive: N,
    val endInclusive: N
) : CodecModifier {
    override fun transformCodecCalling(codecCalling: CodecCalling) = codecCalling.map { type, term, generics ->
        Triple(
            type, CodeBlock.of(
                "%L.validate(%T.checkRange(%L, %L))",
                term,
                TypeMirrors.Codec,
                startInclusive,
                endInclusive,
            ), generics
        )
    }
}

internal class WithinIntModifier(range: WithinInt) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinFloatModifier(range: WithinFloat) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinDoubleModifier(range: WithinDouble) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinLongModifier(range: WithinLong) :
    CodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

