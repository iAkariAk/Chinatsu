package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.github.iakariak.chinatsu.annotation.CodecInfo
import kotlin.reflect.KClass


internal object PropertyInfo {
    fun <T> correspondCodecCalling(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
        emptyModifier: T,
        inferType: (KSType) -> TypeName,
        inferTerm: (KSType) -> CodeBlock?,
        codecDefaultName: String,
        feedback: (propertyType: KSType) -> CorrespondedModifierMarked<T>,
    ): CorrespondedModifierMarked<T> {
        val typeRef = declaration.type
        val type = typeRef.resolve()
        val declQualifiedName = type.declaration.qualifiedName!!.asString()
        val codecCalling = CodecCalling(
            type = inferType(type),
            term = codecInfo?.codecCalling
                ?.replace("~", declQualifiedName)
                ?.replace("^", codecDefaultName)
                ?.let(CodeBlock::of)
                ?: inferTerm(type)
                ?: return feedback(type)
        )
        return CorrespondedModifierMarked.Wrapper(
            source = declaration,
            modifier = emptyModifier,
            inner = CorrespondedModifierMarked.Type<T>(
                source = declaration.type,
                modifier = emptyModifier,
                resolvedType = declaration.type.resolve(),
                arguments = emptyList(),
                codecCalling = codecCalling
            ),
            codecCalling = codecCalling

        )
    }

    fun getName(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
    ): String {
        val pName = declaration.simpleName.asString()
        return codecInfo?.name?.replace("~", pName) ?: pName
    }

    fun <T> scanModifiers(
        annotated: KSAnnotated,
        builtinModifiers: Map<KClass<out Annotation>, (Annotation) -> T>,
        compose: Iterable<T>.() -> T,
        nullability: Nullability = Nullability.PLATFORM
    ): ModifierMarked<T> {
        val modifier = builtinModifiers.mapNotNull { (annotationClass, factory) ->
            annotated.getAnnotationsByType(annotationClass).firstOrNull()?.let { annotation ->
                factory(annotation)
            }
        }.compose() // self

        return when (annotated) {
            is KSPropertyDeclaration -> {
                val typeRef = annotated.type
                ModifierMarked.Wrapper(
                    source = annotated,
                    modifier = modifier,
                    inner = scanModifiers(
                        annotated = typeRef,
                        builtinModifiers = builtinModifiers,
                        compose = compose,
                        nullability = nullability.takeUnless { it == Nullability.PLATFORM }
                            ?: typeRef.resolve().nullability
                    ),
                )
            }

            is KSTypeAlias -> {
                val typeRef = annotated.type
                ModifierMarked.Wrapper(
                    source = annotated,
                    modifier = modifier,
                    inner = scanModifiers(
                        annotated = typeRef,
                        builtinModifiers = builtinModifiers,
                        compose = compose,
                        nullability = nullability.takeUnless { it == Nullability.PLATFORM }
                            ?: annotated.type.resolve().nullability
                    )
                )
            }

            is KSTypeArgument -> {
                val typeRef = annotated.type
                ModifierMarked.Wrapper(
                    source = annotated,
                    modifier = modifier,
                    inner = scanModifiers(
                        annotated = typeRef!!, builtinModifiers = builtinModifiers, compose = compose,
                        nullability = nullability.takeUnless { it == Nullability.PLATFORM }
                            ?: typeRef.resolve().nullability
                    )
                )
            }

            is KSTypeReference -> {
                val typeRef = annotated
                val resolvedType = when (nullability) {
                    Nullability.NULLABLE -> typeRef.resolve().makeNullable()
                    Nullability.NOT_NULL -> typeRef.resolve().makeNotNullable()
                    Nullability.PLATFORM -> typeRef.resolve()
                } // Keep nullability when unwrapping
                (resolvedType.declaration as? KSTypeAlias)?.let { decl ->
                    return scanModifiers(decl, builtinModifiers, compose, resolvedType.nullability)
                }
                ModifierMarked.Type(
                    source = typeRef,
                    modifier = modifier,
                    resolvedType = resolvedType,
                    arguments = resolvedType.arguments.map { argument ->
                        scanModifiers(argument, builtinModifiers, compose)
                    }
                )
            }

            else -> error("Unsupported $annotated")
        }
    }
}

internal sealed interface ModifierMarked<T> {
    val source: KSAnnotated
    val modifier: T

    fun <R> foldIn(initial: R, operation: (R, ModifierMarked<T>) -> R): R

    data class Wrapper<T>(
        override val source: KSAnnotated,
        override val modifier: T,
        val inner: ModifierMarked<T>
    ) : ModifierMarked<T> {
        override fun <R> foldIn(initial: R, operation: (R, ModifierMarked<T>) -> R) =
            operation(inner.foldIn(initial, operation), this)
    }

    data class Type<T>(
        override val source: KSTypeReference,
        override val modifier: T,
        val resolvedType: KSType,
        val arguments: List<ModifierMarked<T>>,
    ) : ModifierMarked<T> {
        override fun <R> foldIn(initial: R, operation: (R, ModifierMarked<T>) -> R): R =
            operation(initial, this)
    }
}

internal sealed interface CorrespondedModifierMarked<T> {
    val source: KSAnnotated
    val modifier: T
    val codecCalling: CodecCalling

    fun <R> foldIn(initial: R, operation: (R, CorrespondedModifierMarked<T>) -> R): R

    data class Wrapper<T>(
        override val source: KSAnnotated,
        override val modifier: T,
        val inner: CorrespondedModifierMarked<T>,
        override val codecCalling: CodecCalling
    ) : CorrespondedModifierMarked<T> {
        override fun <R> foldIn(initial: R, operation: (R, CorrespondedModifierMarked<T>) -> R) =
            operation(inner.foldIn(initial, operation), this)
    }

    data class Type<T>(
        override val source: KSTypeReference,
        override val modifier: T,
        val resolvedType: KSType,
        val arguments: List<CorrespondedModifierMarked<T>>,
        override val codecCalling: CodecCalling
    ) : CorrespondedModifierMarked<T> {
        override fun <R> foldIn(initial: R, operation: (R, CorrespondedModifierMarked<T>) -> R): R =
            operation(initial, this)
    }
}

internal data class CodecCalling(
    val type: TypeName, // exclusive Codec or StreamCodec wrapper
    val term: CodeBlock,
    val genericCodecCallings: List<CodecCalling> = emptyList(),
    val converter: ValueConverter = ValueConverter,
) {
    fun typeBlock() = CodeBlock.of("%T", type)

    inline fun map(transform: (type: TypeName, term: CodeBlock, genericCodecCallings: List<CodecCalling>) -> Triple<TypeName, CodeBlock, List<CodecCalling>>): CodecCalling {
        val (type, term, generics) = transform(type, term, genericCodecCallings)
        return CodecCalling(type, term, generics, converter)
    }
}

internal interface ValueConverter {
    companion object Empty : ValueConverter

    fun from(sourceBlock: CodeBlock): CodeBlock = sourceBlock
    fun to(selfBlock: CodeBlock): CodeBlock = selfBlock
}
