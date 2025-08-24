package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_BUF_NAME
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_VALUE_NAME
import io.github.iakariak.chinatsu.compiler.module.autocodec.PropertyInfo
import io.github.iakariak.chinatsu.compiler.qualificationOf
import io.github.iakariak.chinatsu.compiler.transformIf
import io.github.iakariak.chinatsu.compiler.typeNameStringOf
import java.util.*

internal class StreamCodecPropertyInfo(
    val declaration: KSPropertyDeclaration,
    val source: ByStreamCodec,
) {
    companion object {
        fun fromClass(declaration: KSClassDeclaration, source: ByStreamCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { StreamCodecPropertyInfo(it, source) }

    }

    val modifier = PropertyInfo.scanModifiers(declaration, streamCodecBuiltinModifiers).composed()
    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val type get() = declaration.type.resolve()

    private val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo, declaration, source.defaultCodecName,
        { it.correspondStreamCodecCalling({ modifier.transformCodecCalling(it) }) }
    )

    private val codeCallingVarName = "c_$name"

    fun codecCallingDefineBlock() =
        PropertySpec.builder(codeCallingVarName, source.typeOf(type.toClassName()), KModifier.PRIVATE)
            .initializer(codeCalling)
            .build()

    fun encodeBlock(): CodeBlock = context(this) {
        val type = declaration.type.resolve()
        val arg = CodeBlock.of("%N.%N", P_VALUE_NAME, name)
        val transformedArg = modifier.transformArg(arg)
        val nullableArg = transformedArg.transformIf({ type.isMarkedNullable }) {
            CodeBlock.of(
                "%T.ofNullable(%N.%N)",
                Optional::class.asClassName().parameterizedBy(type.makeNotNullable().toClassName()),
                P_VALUE_NAME,
                name
            )
        }

        return CodeBlock.of(
            "%N.encode(%N, %L)",
            codeCallingVarName,
            P_BUF_NAME,
            nullableArg
        )
    }


    fun decodeBlock(): CodeBlock {
        val result = CodeBlock.of(
            "%N.decode(%N)".transformIf({ type.isMarkedNullable }) { "$it.orElse(null)" },
            codeCallingVarName,
            P_BUF_NAME
        )
        return modifier.transformResult(result)
    }
}


private fun KSType.correspondStreamCodecCalling(
    transform: (CodeBlock) -> CodeBlock = { it }
): CodeBlock {
    val cname = toClassName().toString()
    val qname = declaration.qualifiedName!!.asString()
    when {
        qname == qualificationOf<Optional<*>>() -> {
            val dataType = arguments.first().type!!.resolve()
            return CodeBlock.of(
                "%T.optional(%L)",
                TypeMirrors.ByteBufCodecs,
                dataType.correspondStreamCodecCalling(transform)
            )
        }

        isMarkedNullable -> {
            val dataType = makeNotNullable()
            return CodeBlock.of(
                "%T.optional(%L)",
                TypeMirrors.ByteBufCodecs,
                dataType.correspondStreamCodecCalling(transform)
            )
        }
    }
    val codecCalling = run {
        val field = when (cname) {
            typeNameStringOf<Boolean>() -> "BOOL"
            typeNameStringOf<Byte>() -> "BYTE"
            typeNameStringOf<Short>() -> "SHORT"
            typeNameStringOf<Int>() -> "INT"
            typeNameStringOf<Long>() -> "VAR_LONG"
            typeNameStringOf<Float>() -> "FLOAT"
            typeNameStringOf<Double>() -> "DOUBLE"
            typeNameStringOf<ByteArray>() -> "BYTE_ARRAY"
            typeNameStringOf<String>() -> "STRING_UTF8"
            typeNameStringOf<Optional<Int>>() -> "OPTIONAL_VAR_INT"
            "net.minecraft.nbt.Tag" -> "TAG"
            "net.minecraft.nbt.CompoundTag" -> "COMPOUND_TAG"
            "org.joml.Vector3f" -> "TYPE_VECTOR3F"
            "org.joml.Quaternionf" -> "QUATERNIONF"
            "com.mojang.authlib.GameProfile" -> "GAME_PROFILE"
            else -> null
        } ?: return@run CodeBlock.of("%L.%L", qname, AutoStreamCodec.DEFAULT_NAME)
        CodeBlock.of("%T.%L", TypeMirrors.ByteBufCodecs, field)
    }
    return transform(codecCalling)
}

