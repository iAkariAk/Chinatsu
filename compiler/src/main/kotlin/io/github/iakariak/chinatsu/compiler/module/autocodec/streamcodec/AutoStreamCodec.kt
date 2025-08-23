package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.*
import io.github.iakariak.chinatsu.compiler.ProcessEnv
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.AnnotatedByCodec
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_BUF_NAME
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_VALUE_NAME
import kotlin.reflect.KClass

internal val streamCodecBuiltinModifiers = mapOf<KClass<out Annotation>, (Annotation) -> StreamCodecModifier>(
    WithinInt::class to { within -> WithinIntModifier(within as WithinInt) },
    WithinLong::class to { within -> WithinLongModifier(within as WithinLong) },
    WithinFloat::class to { within -> WithinFloatModifier(within as WithinFloat) },
    WithinDouble::class to { within -> WithinDoubleModifier(within as WithinDouble) }
)

internal data class ByStreamCodec(override val declaration: KSClassDeclaration, override val name: String) :
    AnnotatedByCodec {
    override val defaultCodecName = AutoStreamCodec.DEFAULT_NAME
    override fun typeOf(type: TypeName) = TypeMirrors.StreamCodec.parameterizedBy(TypeMirrors.FriendlyByteBuf, type)

    context(env: ProcessEnv)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, Any> {
        val tType = declaration.toClassName()
        val type = typeOf(tType)
        val decode = FunSpec.builder("decode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(P_BUF_NAME, TypeMirrors.FriendlyByteBuf)
            .returns(tType)
            .addCode("return %T", tType)
            .addCode(
                StreamCodecPropertyInfo.fromClass(declaration, this)
                    .map {
                        buildCodeBlock {
                            add("%N = ", it.name)
                            add(it.decodeBlock())
                        }
                    }
                    .toList()
                    .joinToCode(",\n", prefix = "(\n⇥", suffix = "⇤\n)")
            )
            .build()
        val encode = FunSpec.builder("encode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(P_BUF_NAME, TypeMirrors.FriendlyByteBuf)
            .addParameter(P_VALUE_NAME, tType)
            .addCode(
                StreamCodecPropertyInfo.fromClass(declaration, this)
                    .map {
                        it.encodeBlock()
                    }
                    .toList()
                    .joinToCode("\n")
            )
            .build()
        val initializer = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(type)
            .addFunction(decode)
            .addFunction(encode)
            .build()
        return type to initializer
    }
}
