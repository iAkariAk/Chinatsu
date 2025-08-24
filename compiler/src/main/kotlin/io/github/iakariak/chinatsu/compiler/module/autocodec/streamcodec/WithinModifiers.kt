package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.iakariak.chinatsu.annotation.WithinDouble
import io.github.iakariak.chinatsu.annotation.WithinFloat
import io.github.iakariak.chinatsu.annotation.WithinInt
import io.github.iakariak.chinatsu.annotation.WithinLong
import io.github.iakariak.chinatsu.compiler.TypeMirrors


private val attachedWithin = run {
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
        .addModifiers(KModifier.PRIVATE)
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
}

private val WithinDependencies = StreamCodecModifierDependencies(
    attachedFunctions = setOf(attachedWithin)
)

private class WithinNumberModifier<N : Number>(val startInclusive: N, val endInclusive: N) :
    StreamCodecModifier {
    override val dependencies get() = WithinDependencies

    context(info: StreamCodecPropertyInfo)
    override fun transformCodecCalling(codecCalling: CodeBlock) = CodeBlock.of(
        "%L.%N(%L, %L)",
        codecCalling,
        attachedWithin.name,
        startInclusive,
        endInclusive,
    )
}

internal class WithinIntModifier(range: WithinInt) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinFloatModifier(range: WithinFloat) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinDoubleModifier(range: WithinDouble) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)

internal class WithinLongModifier(range: WithinLong) :
    StreamCodecModifier by WithinNumberModifier(range.startInclusive, range.endInclusive)
