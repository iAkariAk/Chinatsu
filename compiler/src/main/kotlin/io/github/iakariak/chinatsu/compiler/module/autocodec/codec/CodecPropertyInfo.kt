package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.*
import java.util.*


internal class CodecPropertyInfo(
    val declaration: KSPropertyDeclaration,
    val source: ByCodec,
    private val env: ProcessEnv
) {
    companion object {
        context(env: ProcessEnv)
        fun fromClass(declaration: KSClassDeclaration, source: ByCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { CodecPropertyInfo(it, source, env) }
    }

    val codecInfo = declaration.findAnnotation<CodecInfo>()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    private val modifierMarked = scanModifiers(declaration)
    private val correspondedModifierMarked = PropertyInfo.correspondCodecCalling(
        codecInfo = codecInfo,
        declaration = declaration,
        emptyModifier = CodecModifier,
        inferType = { it.toTypeName() },
        inferTerm = { type ->
            type.declaration.findAnnotation<AutoCodec>()?.let { annotation ->
                CodeBlock.of("%T.%N", type, annotation.name)
            }
        },
        codecDefaultName = source.defaultCodecName
    ) {
        context(env) {
            modifierMarked.correspond(true)
        }
    }

    fun constructorBlock(): CodeBlock {
        val arg = buildCodeBlock {
            add(name)
            if (resolvedType.isMarkedNullable) {
                add(".orElse(null)")
            }
        }
        return correspondedModifierMarked.foldIn(arg) { ace, marked ->
            when (marked) {
                is CorrespondedModifierMarked.Type -> {
                    ace.transformIf({ marked.resolvedType.isMarkedNullable }) {
                        CodeBlock.of(
                            "%L.orElse(null)",
                            it
                        )
                    }.let { marked.modifier.transformGetting(it) }
                }

                is CorrespondedModifierMarked.Wrapper -> marked.modifier.transformGetting(ace)
            }

            marked.modifier.transformConstructor(ace)
        }
    }

    fun getterBlock(): CodeBlock = context(env) {
        val arg = CodeBlock.of("obj.%N", declaration.simpleName.asString())
        val getting = correspondedModifierMarked.foldIn(arg) { ace, marked ->
            when (marked) {
                is CorrespondedModifierMarked.Type -> {
                    ace.transformIf({ marked.resolvedType.isMarkedNullable }) {
                        CodeBlock.of(
                            "%T.ofNullable(%L)",
                            Optional::class.asClassName(),
                            it
                        )
                    }.let { marked.modifier.transformGetting(it) }
                }

                is CorrespondedModifierMarked.Wrapper -> marked.modifier.transformGetting(ace)
            }
        }
        return CodeBlock.of(
            "%L.%N(%S).forGetter { obj -> %L }",
            correspondedModifierMarked.codecCalling.term,
            if (resolvedType.isMarkedNullable || resolvedType.isOptional) "optionalFieldOf" else "fieldOf",
            name,
            getting
        )
    }
}


context(env: ProcessEnv)
internal fun ModifierMarked<CodecModifier>.correspond(
    isNullableImplByOuter: Boolean = false  // i.e. call from property
): CorrespondedModifierMarked<CodecModifier> {
    if (this is ModifierMarked.Wrapper) {
        val correspondedInner = inner.correspond(isNullableImplByOuter)
        return CorrespondedModifierMarked.Wrapper(
            source = source,
            modifier = modifier,
            inner = correspondedInner,
            codecCalling = modifier.transformCodecCalling(correspondedInner.codecCalling)
        )
    }

    return (this as ModifierMarked.Type).correspond(isNullableImplByOuter)
}


context(env: ProcessEnv)
internal fun ModifierMarked.Type<CodecModifier>.correspond(
    isNullableImplByOuter: Boolean = false  // i.e. call from property
): CorrespondedModifierMarked.Type<CodecModifier> {
    fun correspondWith(codecCalling: CodecCalling): CorrespondedModifierMarked.Type<CodecModifier> =
        CorrespondedModifierMarked.Type(
            source = source,
            modifier = modifier,
            resolvedType = resolvedType,
            arguments = arguments.map { it.correspond() },
            codecCalling = codecCalling
        )

    run {
        val dataType = when {
            resolvedType.isOptional -> arguments.first()
            resolvedType.isMarkedNullable -> copy(resolvedType = resolvedType.makeNotNullable())
            else -> return@run
        }
        val converter = object : ValueConverter {
            override fun from(sourceBlock: CodeBlock) =
                CodeBlock.of("%T.ofNullable(%L)", Optional::class.asTypeName(), sourceBlock)

            override fun to(selfBlock: CodeBlock) =
                CodeBlock.of("%L.orElse(null)", selfBlock)
        }
        val correspondedDataType = dataType.correspond()
        val dateTypeCodeCalling = correspondedDataType.codecCalling
        val actualOptionalType = Optional::class.asTypeName().parameterizedBy(dateTypeCodeCalling.type)
        if (isNullableImplByOuter) {
            val codecCalling = dateTypeCodeCalling.copy(
                type = actualOptionalType,
                converter = converter
            ) // coupe with optionFieldOf
            return correspondWith(codecCalling)
        } else {
            if (!env.options.enableWrapNullableInCodec) {
                env.logger.error(
                    "The nested nullable in Codec in cannot be allowed. " +
                            "You can add ksp arg `wrapNullableInCodec` to map them automatically",
                    source
                )
            } else {
                val placeholderType = BOOLEAN
                val wrapperType = TypeMirrors.DFEither.parameterizedBy(dateTypeCodeCalling.type, placeholderType)
                return correspondWith(
                    CodecCalling(
                        type = actualOptionalType,
                        term = CodeBlock.of(
                            "%1T.either<%2T, %3T>(%4L, %1T.BOOL)" +
                                    ".xmap(" +
                                    "{ either -> either.left()}," +
                                    "{ optional -> optional.map { %5T.left<%2T, %3T>(it)}.orElse(%5T.right(false)) }" +
                                    ")",
                            TypeMirrors.Codec,
                            dateTypeCodeCalling.type,
                            placeholderType,
                            dateTypeCodeCalling.term,
                            wrapperType,
                        ),
                        converter = converter
                    )
                )
            }
        }
    }

    val declaration = resolvedType.declaration
    val qname = declaration.qualifiedName!!.asString()

    val isDPEither = qname == TypeMirrors.DFEither.canonicalName
    if (isDPEither) {
        val leftMarked = arguments[0]
        val rightMarked = arguments[1]
        val leftCodecCalling = leftMarked.correspond().codecCalling
        val rightCodecCalling = rightMarked.correspond().codecCalling
        return correspondWith(
            CodecCalling(
                type = TypeMirrors.DFEither.parameterizedBy(leftCodecCalling.type, rightCodecCalling.type),
                term = CodeBlock.of(
                    "%T.either<%T, %T>(%L, %L)",
                    TypeMirrors.Codec,
                    leftCodecCalling.type,
                    rightCodecCalling.type,
                    leftCodecCalling.term,
                    rightCodecCalling.term
                ),
                genericCodecCallings = listOf(leftCodecCalling, rightCodecCalling)
            )
        )
    }

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName
    if (isDPPair || isKPair) {
        val firstMarked = arguments[0]
        val secondMarked = arguments[1]
        val firstCodecCalling = firstMarked.correspond().codecCalling
        val secondCodecCalling = secondMarked.correspond().codecCalling
        println(secondCodecCalling.converter)

        return correspondWith(
            CodecCalling(
                type = resolvedType.toTypeName().erasedAsClass()!!
                    .parameterizedBy(firstCodecCalling.type, secondCodecCalling.type),
                term = CodeBlock.of(
                    "%T.pair<%T, %T>(%L, %L)",
                    TypeMirrors.Codec,
                    firstCodecCalling.type,
                    secondCodecCalling.type,
                    firstCodecCalling.term,
                    secondCodecCalling.term
                ).transformIf({ isKPair }) {
                    CodeBlock.of(
                        "%L.xmap({ dfpair -> dfpair.first to dfpair.second}, {(first, second) -> %T(first, second)})",
                        it,
                        TypeMirrors.DFPair
                    )
                }.transformIf({
                    listOf(
                        firstCodecCalling.converter,
                        secondCodecCalling.converter
                    ).any { it != ValueConverter.Empty }
                }) {
                    val first = CodeBlock.of("first")
                    val second = CodeBlock.of("second")
                    CodeBlock.of(
                        "%1L.xmap({ (%2L, %3L) -> %4L to %5L }, { (%2L, %3L) -> %6L to %7L })",
                        it,
                        first,
                        second,
                        firstCodecCalling.converter.to(first),
                        secondCodecCalling.converter.to(second),
                        firstCodecCalling.converter.from(first),
                        secondCodecCalling.converter.from(second),
                    )
                },
                genericCodecCallings = listOf(firstCodecCalling, secondCodecCalling)
            )
        )
    }

    if (resolvedType.isList) {
        val markedT = arguments.first()
        val codecCalling = markedT.correspond().codecCalling
        return correspondWith(
            CodecCalling(
                type = List::class.asTypeName().parameterizedBy(codecCalling.type),
                term = CodeBlock.of(
                    "%T.list(%L)",
                    TypeMirrors.Codec,
                    codecCalling.term
                ).transformIf({ codecCalling.converter != ValueConverter.Empty }) {
                    val item = CodeBlock.of("item")
                    CodeBlock.of(
                        "%1L.xmap({ %2L -> %3L }, { %2L -> %4L to %5L })",
                        it,
                        item,
                        codecCalling.converter.to(item),
                        codecCalling.converter.from(item),
                    )
                    CodeBlock.of(
                        "%L.map { %L -> %L }",
                        it, item, codecCalling.converter.from(item)
                    )
                },
                genericCodecCallings = listOf(codecCalling)
            ))
    }


    if (resolvedType.isMarkedNullable) {
        val markedT = arguments[0]
        val markedV = arguments[1]
        val codecCallingT = markedT.correspond().codecCalling
        val codecCallingV = markedV.correspond().codecCalling
        return correspondWith(
            CodecCalling(
                type = Map::class.asTypeName().parameterizedBy(codecCallingT.type, codecCallingV.type),
                term = CodeBlock.of(
                    "%T.unboundedMap<%T, %T>(%L, %L)",
                    TypeMirrors.Codec,
                    codecCallingT.type,
                    codecCallingV.type,
                    codecCallingT.term,
                    codecCallingV.term
                ),
                genericCodecCallings = listOf(codecCallingT, codecCallingV)
            )
        )
    }

    val guessedTerm = run {
        val field = when (qname) {
            qualificationOf<Boolean>() -> "BOOL"
            qualificationOf<Byte>() -> "BYTE"
            qualificationOf<Short>() -> "SHORT"
            qualificationOf<Int>() -> "INT"
            qualificationOf<Long>() -> "LONG"
            qualificationOf<Float>() -> "FLOAT"
            qualificationOf<Double>() -> "DOUBLE"
            qualificationOf<String>() -> "STRING"
            else -> null
        } ?: return@run CodeBlock.of("%L.%L", qname, AutoCodec.DEFAULT_NAME)
        CodeBlock.of("%T.%L", TypeMirrors.Codec, field)
    }
    val codecCalling = CodecCalling(
        type = resolvedType.toTypeName(), // Most fundamental origin
        term = guessedTerm
    )
    return correspondWith(modifier.transformCodecCalling(codecCalling))
}

