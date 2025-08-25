package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.iakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.annotation.DelegateCodec
import io.github.iakariak.chinatsu.compiler.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_BUF_NAME
import io.github.iakariak.chinatsu.compiler.module.autocodec.P_VALUE_NAME
import io.github.iakariak.chinatsu.compiler.module.autocodec.PropertyInfo
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.correspondCodecCalling
import java.util.*

internal class StreamCodecPropertyInfo(
    val declaration: KSPropertyDeclaration,
    val source: ByStreamCodec,
    env: ProcessEnv
) {
    companion object {
        context(env: ProcessEnv)
        fun fromClass(declaration: KSClassDeclaration, source: ByStreamCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { StreamCodecPropertyInfo(it, source, env) }

    }

    val modifier = PropertyInfo.scanModifiers(declaration, streamCodecBuiltinModifiers).composed()
    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    private val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo, declaration, source.defaultCodecName
    ) {
        context(env) {
            it.correspondStreamCodecCalling(declaration, declaration.type) {
                modifier.transformCodecCalling(it)
            }
        }
    }

    private val codeCallingVarName = "c_$name"

    fun codecCallingDefineBlock() =
        PropertySpec.builder(codeCallingVarName, source.typeOf(resolvedType.toTypeName()), KModifier.PRIVATE)
            .initializer(codeCalling)
            .build()

    fun encodeBlock(): CodeBlock = context(this) {
        val type = resolvedType
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
            "%N.decode(%N)".transformIf({ resolvedType.isMarkedNullable }) { "$it.orElse(null)" },
            codeCallingVarName,
            P_BUF_NAME
        )
        return modifier.transformResult(result)
    }
}

context(env: ProcessEnv)
internal fun KSType.correspondStreamCodecCalling(
    propertySource: KSPropertyDeclaration? = null,
    source: KSTypeReference? = null,
    transform: (CodeBlock) -> CodeBlock = { it }
): CodeBlock {
    fun delegateByCodec(): CodeBlock {
        val codec = correspondCodecCalling(propertySource, source)
        return CodeBlock.of("%T.fromCodec(%L)", TypeMirrors.ByteBufCodecs, codec)
    }

    source?.let {
        if (it.getAnnotationsByType(DelegateCodec::class).toList().isNotEmpty()) {
            return delegateByCodec()
        }
    }

    val qname = declaration.qualifiedName!!.asString()

    run {
        val dataType = when {
            isOptional -> arguments.first().type!!.resolve()
            isMarkedNullable -> makeNotNullable()
            else -> return@run
        }
        return CodeBlock.of(
            "%T.optional(%L)",
            TypeMirrors.ByteBufCodecs,
            dataType.correspondStreamCodecCalling(propertySource, source, transform)
        )
    }

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName

    if (isDPPair || isKPair) {
        return delegateByCodec()
    }

    if (isList) {
        val tRef = arguments.first().type!!
        return CodeBlock.of(
            "%T.list().apply(%L)",
            TypeMirrors.ByteBufCodecs,
            tRef.resolve().correspondStreamCodecCalling(null, tRef, transform)
        )
    }

    if (isMap) {
        return delegateByCodec()
    }

    val codecCalling = run {
        val field = when (qname) {
            qualificationOf<Boolean>() -> "BOOL"
            qualificationOf<Byte>() -> "BYTE"
            qualificationOf<Short>() -> "SHORT"
            qualificationOf<Int>() -> "INT"
            qualificationOf<Long>() -> "VAR_LONG"
            qualificationOf<Float>() -> "FLOAT"
            qualificationOf<Double>() -> "DOUBLE"
            qualificationOf<ByteArray>() -> "BYTE_ARRAY"
            qualificationOf<String>() -> "STRING_UTF8"
            qualificationOf<Optional<Int>>() -> "OPTIONAL_VAR_INT"
            "net.minecraft.nbt.Tag" -> "TAG"
            "net.minecraft.nbt.CompoundTag" -> "COMPOUND_TAG"
            "org.joml.Vector3f" -> "TYPE_VECTOR3F"
            "org.joml.Quaternionf" -> "QUATERNIONF"
            "com.mojang.authlib.GameProfile" -> "GAME_PROFILE"
            else -> null
        } ?: return@run CodeBlock.of("%L.%L", qname, AutoStreamCodec.DEFAULT_NAME)
        CodeBlock.of("%T.%L", TypeMirrors.ByteBufCodecs, field)
    }
    return transform(codecCalling.transformIf({ source != null }) {
        val modifier = PropertyInfo.scanModifiers(source!!, streamCodecBuiltinModifiers).composed()
        context(null) {
            modifier.transformCodecCalling(it)
        }
    })
}

