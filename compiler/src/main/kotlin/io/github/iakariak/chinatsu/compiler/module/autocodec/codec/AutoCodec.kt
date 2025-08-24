package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.*
import io.github.iakariak.chinatsu.compiler.ProcessEnv
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.AnnotatedByCodec
import java.util.*
import kotlin.reflect.KClass

internal val codecBuiltinModifiers = mapOf<KClass<out Annotation>, (Annotation) -> CodecModifier>(
    WithinInt::class to { within -> WithinIntModifier(within as WithinInt) },
    WithinLong::class to { within -> WithinLongModifier(within as WithinLong) },
    WithinFloat::class to { within -> WithinFloatModifier(within as WithinFloat) },
    WithinDouble::class to { within -> WithinDoubleModifier(within as WithinDouble) }
)

internal data class ByCodec(
    override val declaration: KSClassDeclaration,
    override val name: String
) : AnnotatedByCodec {
    override val defaultCodecName = AutoCodec.DEFAULT_NAME
    override fun typeOf(type: TypeName) = TypeMirrors.Codec.parameterizedBy(type)

    context(env: ProcessEnv)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, Any> {
        val tType = declaration.toClassName()
        val type = typeOf(tType)
        val infos = CodecPropertyInfo.fromClass(declaration, this@ByCodec).toList()
        val infosReversed = infos.reversed()

        fun CodeBlock.Builder.addCurryConstructor(index: Int = 0) {
            if (index >= infos.size) {
                val args = infos
                    .joinToCode { info ->
                        info.constructorDescriptorBlock()
                    }
                add("%T(%L)\n", declaration.toClassName(), args)
                return
            }

            val info = infos[index]
            val argType = if (info.type.isMarkedNullable) {
                val t = info.type.makeNotNullable().toClassName()
                Optional::class.asClassName().parameterizedBy(t)
            } else {
                info.type.toClassName()
            }

            add("%T {  %N/* arg$index */: %T ->\n", TypeMirrors.JFunction, info.name, argType)
            indent()
            addCurryConstructor(index + 1)
            unindent()
            add("}")
            if (index >= 1) {
                add("\n")
            }
        }

        fun CodeBlock.Builder.addCurryApRecursively(index: Int = 0) {
            if (index >= infosReversed.size) return

            val info = infosReversed[index]
            add("instance.ap(\n")
            withIndent {
                if (index == infosReversed.lastIndex) {
                    addCurryConstructor()
                }
                addCurryApRecursively(index + 1)
                add(",\n")
                add(info.getterDescriptorBlock())
                add("\n")
            }
            add(")")
            if (index == 0) {
                add("\n")
            }
        }


        val initializer = buildCodeBlock {
            add("\n%T.create { instance ->\n", TypeMirrors.RecordCodecBuilder)
            withIndent {
                addCurryApRecursively()
            }
            add("}")
        }
        return type to initializer
    }
}
