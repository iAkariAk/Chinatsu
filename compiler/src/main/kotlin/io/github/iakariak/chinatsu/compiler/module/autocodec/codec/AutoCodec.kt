package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.*
import io.github.iakariak.chinatsu.compiler.ProcessEnv
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.AnnotatedByCodec
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
        val className = declaration.toClassName()
        val tType = className
        val type = typeOf(tType)
        val infos = CodecPropertyInfo.fromClass(declaration, this@ByCodec).toList()
        val initializer = buildCodeBlock {
            val instance = CodeBlock.of("instance")
            add("\n%T.create { %L ->\n", TypeMirrors.RecordCodecBuilder, instance)
            withIndent {
                val args = infos.map { it.getterDescriptorBlock() }
                val pointed = buildCodeBlock {
                    add("%L.point { %L ->\n", instance,infos.joinToCode { CodeBlock.of("%N", it.name) })
                    withIndent {
                        add("%T(\n", className)
                        withIndent {
                            infos.forEach { info ->
                                add("%N = %L,\n", info.name, info.constructorDescriptorBlock())
                            }
                        }
                        add(")\n")
                    }
                    add("}")
                }
                val ap = CodecAttachment.ap(infos.size, instance, pointed, args)
                add(ap)
            }
            add("}")
        }
        env.attach(CodecAttachment)
        return type to initializer
    }
}


