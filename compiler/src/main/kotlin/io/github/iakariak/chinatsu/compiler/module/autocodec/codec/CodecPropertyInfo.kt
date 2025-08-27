package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.CodecCalling
import io.github.iakariak.chinatsu.compiler.module.autocodec.ModifierMarked
import io.github.iakariak.chinatsu.compiler.module.autocodec.PropertyInfo
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

    private val modifierMarked = scanModifiers(declaration)
    val codecInfo = declaration.findAnnotation<CodecInfo>()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo = codecInfo,
        declaration = declaration,
        inferType = { it.toTypeName() },
        inferTerm = { type ->
            type.declaration.findAnnotation<AutoCodec>()?.let { annotation ->
                CodeBlock.of("%T.%N", type, annotation.name)
            }
        },
        codecDefaultName = source.defaultCodecName
    ) {
        context(env) {
            modifierMarked.correspondCodecCalling()
        }
    }

    fun constructorDescriptorBlock(): CodeBlock {
        val arg = buildCodeBlock {
            add(name)
            if (resolvedType.isMarkedNullable) {
                add(".orElse(null)")
            }
        }
        return modifierMarked.foldIn(arg) { ace, marked ->
            marked.modifier.transformConstructor(ace)
        }
    }

    fun getterDescriptorBlock(): CodeBlock = context(env) {
        val arg = CodeBlock.of("obj.%N", declaration.simpleName.asString())
        val transformedArg = modifierMarked.foldIn(arg) { ace, marked ->
            marked.modifier.transformGetting(ace)
        }
        val getting = transformedArg.transformIf({ resolvedType.isMarkedNullable }) {
            CodeBlock.of(
                "%T.ofNullable(%L)",
                Optional::class.asClassName(),
                it
            )
        }
        return CodeBlock.of(
            "%L.%N(%S).forGetter { obj -> %L }",
            codeCalling.term,
            if (resolvedType.isMarkedNullable || resolvedType.isOptional) "optionalFieldOf" else "fieldOf",
            name,
            getting
        )
    }
}

context(env: ProcessEnv)
internal fun ModifierMarked<CodecModifier>.correspondCodecCalling(
): CodecCalling {
    if (this is ModifierMarked.Wrapper) {
        return inner.correspondCodecCalling()
    }
    this as ModifierMarked.Type

    run {
        val dataType = when {
            resolvedType.isOptional -> arguments.first()
            resolvedType.isMarkedNullable -> copy(resolvedType = resolvedType.makeNotNullable())
            else -> return@run
        }
        return dataType.correspondCodecCalling()
    }

    val declaration = resolvedType.declaration
    val qname = declaration.qualifiedName!!.asString()

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName
    if (isDPPair || isKPair) {
        val firstMarked = arguments[0]
        val secondMarked = arguments[1]
        val firstCodecCalling = firstMarked.correspondCodecCalling()
        val secondCodecCalling = secondMarked.correspondCodecCalling()
        return CodecCalling(
            type = resolvedType.toTypeName().erasedAsClass()!!.parameterizedBy(firstCodecCalling.type, secondCodecCalling.type),
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
            }
        )
    }

    if (resolvedType.isList) {
        val markedT = arguments.first()
        val codecCalling = markedT.correspondCodecCalling()
        return CodecCalling(
            type = List::class.asTypeName().parameterizedBy(codecCalling.type),
            CodeBlock.of(
                "%T.list(%L)",
                TypeMirrors.Codec,
                codecCalling.term
            )
        )
    }


    if (resolvedType.isMarkedNullable) {
        val markedT = arguments[0]
        val markedV = arguments[1]
        val codecCallingT = markedT.correspondCodecCalling()
        val codecCallingV = markedV.correspondCodecCalling()
        return CodecCalling(
            type = Map::class.asTypeName().parameterizedBy(codecCallingT.type, codecCallingV.type),
            term = CodeBlock.of(
                "%T.unboundedMap<%T, %T>(%L, %L)",
                TypeMirrors.Codec,
                codecCallingT.type,
                codecCallingV.type,
                codecCallingT.term,
                codecCallingV.term
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
    return modifier.transformCodecCalling(codecCalling)
}

