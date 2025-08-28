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
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.correspond
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

    val codecInfo = declaration.findAnnotation<CodecInfo>()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    private val modifierMarked = StreamCodecPropertyInfo.scanModifiers(declaration)

    private val correspondedModifierMarked = PropertyInfo.correspondCodecCalling(
        codecInfo,
        declaration,
        emptyModifier = StreamCodecModifier,
        inferType = { it.toTypeName() },
        inferTerm = { type ->
            type.declaration.findAnnotation<AutoStreamCodec>()?.let { annotation ->
                CodeBlock.of("%T.%N", type, annotation.name)
            }
        },
        codecDefaultName = source.defaultCodecName
    ) { type ->
        context(env) {
            modifierMarked.correspond()
        }
    }

    private val codeCallingVarName = "c_$name"

    fun codecCallingDefineBlock(): PropertySpec {
        val codecCalling = correspondedModifierMarked.codecCalling
        return PropertySpec.builder(codeCallingVarName, source.typeOf(codecCalling.type), KModifier.PRIVATE)
            .initializer(codecCalling.term)
            .build()
    }

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
                    }.let { marked.modifier.transformArg(it) }
                }

                is ModifierMarked.Wrapper -> marked.modifier.transformArg(ace)
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
                    ace.transformIf({ marked.resolvedType.isMarkedNullable }) {
                        CodeBlock.of("%L.orElse(null)", it)
                    }.let { marked.modifier.transformResult(it) }
                }

                is ModifierMarked.Wrapper -> marked.modifier.transformResult(ace)
            }
        }
    }
}


context(env: ProcessEnv)
internal fun ModifierMarked<StreamCodecModifier>.correspond(): CorrespondedModifierMarked<StreamCodecModifier> {
    if (this is ModifierMarked.Wrapper) {
        val correspondedInner = inner.correspond()
        return CorrespondedModifierMarked.Wrapper(
            source = source,
            modifier = modifier,
            inner = correspondedInner,
            codecCalling = modifier.transformCodecCalling(correspondedInner.codecCalling)
        )
    }

    return (this as ModifierMarked.Type).correspond()
}

context(env: ProcessEnv)
internal fun ModifierMarked.Type<StreamCodecModifier>.correspond(): CorrespondedModifierMarked.Type<StreamCodecModifier> {
    fun correspondWith(codecCalling: CodecCalling): CorrespondedModifierMarked.Type<StreamCodecModifier> =
        CorrespondedModifierMarked.Type(
            source = source,
            modifier = modifier,
            resolvedType = resolvedType,
            arguments = arguments.map { it.correspond() },
            codecCalling = codecCalling
        )

    fun delegateByCodec(): CodecCalling {
        val marked = CodecPropertyInfo.scanModifiers(source)
        val codec = marked.correspond().codecCalling
        return codec.map { type, term, _ ->
            Triple(
                type,
                CodeBlock.of(
                    "%T.fromCodec(%L)",
                    TypeMirrors.ByteBufCodecs,
                    term
                ),
                codec.genericCodecCallings
            )
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
        val codecCalling = dataType.correspond().codecCalling
        return correspondWith(
            CodecCalling(
                type = Optional::class.asTypeName().parameterizedBy(codecCalling.type),
                term = CodeBlock.of(
                    "%T.optional(%L)",
                    TypeMirrors.ByteBufCodecs,
                    codecCalling.term
                ),
                genericCodecCallings = listOf(codecCalling)
            )
        )
    }

    val declaration = resolvedType.declaration
    val qname = declaration.qualifiedName!!.asString()

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName

    if (isDPPair || isKPair) {
        return correspondWith(delegateByCodec())
    }

    if (resolvedType.isList) {
        val markedT = arguments.first()
        val codecCalling = markedT.correspond().codecCalling
        return correspondWith(
            CodecCalling(
                type = List::class.asTypeName().parameterizedBy(codecCalling.type),
                term = CodeBlock.of(
                    "%T.list<%T, %T>().apply(%L)",
                    TypeMirrors.ByteBufCodecs,
                    TypeMirrors.ByteBuf,
                    codecCalling.type,
                    codecCalling.term
                ),
                genericCodecCallings = listOf(codecCalling)
            )
        )
    }

    if (resolvedType.isMap) {
        return correspondWith(delegateByCodec())
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
    return correspondWith(modifier.transformCodecCalling(codecCalling))
}

