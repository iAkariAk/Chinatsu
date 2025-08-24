package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.*
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

    val modifier = PropertyInfo.scanModifiers(declaration, codecBuiltinModifiers).composed()
    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val resolvedType get() = declaration.type.resolve()

    val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo, declaration, source.defaultCodecName
    ) {
        context(env) {
            it.correspondCodecCalling(declaration.type) {
                modifier.transformCodecCalling(it)
            }
        }
    }

    fun constructorDescriptorBlock(): CodeBlock {
        val arg = buildCodeBlock {
            add(name)
            if (resolvedType.isMarkedNullable) {
                add(".orElse(null)")
            }
        }
        return modifier.transformConstructor(arg)
    }

    fun getterDescriptorBlock(): CodeBlock = context(env) {
        val arg = CodeBlock.of("obj.%N", declaration.simpleName.asString())
        val transformedArg = modifier.transformGetting(arg)
        val getting = transformedArg.transformIf({ resolvedType.isMarkedNullable }) {
            CodeBlock.of(
                "%T.ofNullable(%L)",
                Optional::class.asClassName(),
                it
            )
        }
        return CodeBlock.of(
            "%L.%N(%S).forGetter { obj -> %L }",
            codeCalling,
            if (resolvedType.isMarkedNullable || resolvedType.isOptional) "optionalFieldOf" else "fieldOf",
            name,
            getting
        )
    }
}

context(env: ProcessEnv)
internal fun KSType.correspondCodecCalling(
    source: KSTypeReference? = null,
    transform: (CodeBlock) -> CodeBlock = { it }
): CodeBlock {
    val qname = declaration.qualifiedName!!.asString()

    run {
        val dataType = when {
            isOptional -> arguments.first().type!!.resolve()
            isMarkedNullable -> makeNotNullable()
            else -> return@run
        }
        return dataType.correspondCodecCalling(source, transform)
    }

    val isDPPair = qname == TypeMirrors.DFPair.canonicalName
    val isKPair = qname == Pair::class.qualifiedName
    if (isDPPair || isKPair) {
        val firstRef = arguments[0].type!!
        val firstType = firstRef.resolve()
        val secondRef = arguments[1].type
        val secondType = secondRef!!.resolve()
        val firstCodecCalling = firstType.correspondCodecCalling(firstRef, transform)
        val secondCodecCalling = secondType.correspondCodecCalling(secondRef, transform)
        return CodeBlock.of("%T.pair(%L, %L)", TypeMirrors.Codec, firstCodecCalling, secondCodecCalling)
            .transformIf({ isKPair }) {
                CodeBlock.of(
                    "%L.xmap({ dfpair -> dfpair.first to dfpair.second}, {(first, second) -> %T(first, second)})",
                    it,
                    TypeMirrors.DFPair
                )
            }
    }

    if (isList) {
        val tRef = arguments.first().type!!
        return CodeBlock.of(
            "%T.list(%L)",
            TypeMirrors.Codec,
            tRef.resolve().correspondCodecCalling(tRef, transform)
        )
    }


    if (isMap) {
        val kRef = arguments[0].type!!
        val vRef = arguments[1].type!!
        return CodeBlock.of(
            "%T.unboundedMap(%L, %L)",
            TypeMirrors.Codec,
            kRef.resolve().correspondCodecCalling(kRef, transform),
            vRef.resolve().correspondCodecCalling(vRef, transform)
        )
    }


    val codecCalling = run {
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
    return transform(codecCalling.transformIf({ source != null }) {
        val modifier = PropertyInfo.scanModifiers(source!!, codecBuiltinModifiers).composed()
        context(null) {
            modifier.transformCodecCalling(it)
        }
    })
}

