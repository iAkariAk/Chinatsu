package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.CodecCalling


private val withIn = StreamCodecAttachment.install(run {
    val b = TypeVariableName("B")
    var v = TypeVariableName("V")
    v = v.copy(
        bounds = listOf(
            Number::class.asClassName(),
            Comparable::class.asClassName().parameterizedBy(v)
        )
    )
    var targetType = TypeMirrors.StreamCodec.parameterizedBy(b, v)
    FunSpec.builder("within")
        .addTypeVariable(b)
        .addTypeVariable(v)
        .receiver(targetType)
        .addParameter("startInclusive", v)
        .addParameter("endInclusive", v)
        .returns(targetType)
        .addCode(
            $$"""
                |val checker = { num: %T ->
                |  check (num in startInclusive..endInclusive) {
                |    "Value $num outside of range[$startInclusive, $endInclusive])"
                |  }
                |  num
                |}
                |
                """.trimMargin(), v
        )
        .addStatement("return this.map(checker, checker)")
        .build()
}, isExtension = true)


private class WithinNumberModifier<N : Number>(val startInclusive: N, val endInclusive: N) :
    StreamCodecModifier {
    override fun transformCodecCalling(codecCalling: CodecCalling) = codecCalling.map { type, term, generics ->
        Triple(
            type, CodeBlock.of(
                "%L.%M(%L, %L)",
                term,
                withIn,
                startInclusive,
                endInclusive,
            ), generics
        )
    }
}

internal class WithinIntModifier(range: WithinInt) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinFloatModifier(range: WithinFloat) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinDoubleModifier(range: WithinDouble) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinLongModifier(range: WithinLong) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)
