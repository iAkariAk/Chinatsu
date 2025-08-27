package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.iakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.annotation.DelegateCodec
import io.github.iakariak.chinatsu.compiler.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.CodecPropertyInfo
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.correspondCodecCalling
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.scanModifiers
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

    private val modifierMarked = StreamCodecPropertyInfo.scanModifiers(declaration)
    val codecInfo = declaration.findAnnotation<CodecInfo>()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo,
        declaration,
        inferType = { it.toTypeName() },
        inferTerm = { type ->
            type.declaration.findAnnotation<AutoStreamCodec>()?.let { annotation ->
                CodeBlock.of("%T.%N", type, annotation.name)
            }
        },
        codecDefaultName = source.defaultCodecName
    ) { type ->
        context(env) {
            modifierMarked.correspondStreamCodecCalling()
        }
    }

    private val codeCallingVarName = "c_$name"

    fun codecCallingDefineBlock() =
        PropertySpec.builder(codeCallingVarName, source.typeOf(codeCalling.type), KModifier.PRIVATE)
            .initializer(codeCalling.term)
            .build()

    fun encodeBlock(): CodeBlock = context(this) {
        val type = resolvedType
        val arg = CodeBlock.of("%N.%N", P_VALUE_NAME, name)
        val transformedArg = modifierMarked.foldIn(arg) { ace, marked ->
            when (marked) {
                is ModifierMarked.Type -> {
                    ace.transformIf({ marked.resolvedType.nullability == Nullability.NULLABLE }) {
                        CodeBlock.of(
                            "%T.ofNullable(%N.%N)",
                            Optional::class.asClassName().parameterizedBy(type.makeNotNullable().toClassName()),
                            P_VALUE_NAME,
                            name
                        )
                    }
                }

                is ModifierMarked.Wrapper -> marked.modifier.transformResult(ace)
            }
        }

        return CodeBlock.of(
            "%N.encode(%N, %L)",
            codeCallingVarName,
            P_BUF_NAME,
            transformedArg
        )
    }


    fun decodeBlock(): CodeBlock {
        val result = CodeBlock.of(
            "%N.decode(%N)",
            codeCallingVarName,
            P_BUF_NAME
        )
        return modifierMarked.foldIn(result) { ace, marked ->
            when (marked) {
                is ModifierMarked.Type -> {
                    ace.transformIf({ marked.resolvedType.nullability == Nullability.NULLABLE }) {
                        CodeBlock.of("%L.orElse(null)", it)
                    }
                }

                is ModifierMarked.Wrapper -> marked.modifier.transformResult(ace)
            }
        }
    }
}

context(env: ProcessEnv)
internal fun ModifierMarked<StreamCodecModifier>.correspondStreamCodecCalling(): CodecCalling {
    if (this is ModifierMarked.Wrapper) {
        return inner.correspondStreamCodecCalling()
    }
    this as ModifierMarked.Type

    fun delegateByCodec(): CodecCalling {
        val marked = CodecPropertyInfo.scanModifiers(source)
        val codec = marked.correspondCodecCalling()
        return codec.map { type, term ->
            type to CodeBlock.of("%T.fromCodec(%L)", TypeMirrors.ByteBufCodecs, term)
        }
    }

    if (source.hasAnnotation<DelegateCodec>()) {
        delegateByCodec()
    }

    run {
        val dataType = when {
            resolvedType.isOptional -> arguments.first()
            resolvedType.isMarkedNullable -> copy(resolvedType = resolvedType.makeNotNullable())
            else -> return@run
        }
        val codecCalling = dataType.correspondStreamCodecCalling()
        return CodecCalling(
            type = Optional::class.asTypeName().parameterizedBy(codecCalling.type),
            term = CodeBlock.of(
                "%T.optional(%L)",
                TypeMirrors.ByteBufCodecs,
                codecCalling.term
            )
        )
    }

    val declaration = resolvedType.declaration
    val qname = declaration.qualifiedName!!.asString()

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName

    if (isDPPair || isKPair) {
        return delegateByCodec()
    }

    if (resolvedType.isList) {
        val markedT = arguments.first()
        val codecCalling = markedT.correspondStreamCodecCalling()
        return CodecCalling(
            type = List::class.asTypeName().parameterizedBy(codecCalling.type),
            term = CodeBlock.of(
                "%T.list<%T, %T>().apply(%L)",
                TypeMirrors.ByteBufCodecs,
                TypeMirrors.ByteBuf,
                codecCalling.type,
                codecCalling.term
            )
        )
    }

    if (resolvedType.isMap) {
        return delegateByCodec()
    }

    val guessedTerm = run {
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
    val codecCalling = CodecCalling(
        type = resolvedType.toTypeName(), // Most fundamental origin
        term = guessedTerm
    )
    return modifier.transformCodecCalling(codecCalling)
}

