package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.module.autocodec.PropertyInfo
import io.github.iakariak.chinatsu.compiler.transformIf
import io.github.iakariak.chinatsu.compiler.typeNameStringOf
import java.util.*


internal class CodecPropertyInfo(
    val declaration: KSPropertyDeclaration,
    val source: ByCodec,
) {
    companion object {
        fun fromClass(declaration: KSClassDeclaration, source: ByCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { CodecPropertyInfo(it, source) }
    }

    val modifier = PropertyInfo.scanModifiers(declaration, codecBuiltinModifiers).composed()
    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val name = PropertyInfo.getName(codecInfo, declaration)
    val type get() = declaration.type.resolve()

    val codeCalling = PropertyInfo.getCodecCalling(
        codecInfo, declaration, source.defaultCodecName,
        { it.correspondCodecCalling { modifier.descriptorBlockTransformer.transformCodecCalling(it) } }
    )

    fun constructorDescriptorBlock(argName: String): CodeBlock {
        val arg = buildCodeBlock {
            add(argName)
            if (type.isMarkedNullable) {
                add(".orElse(null)")
            }
        }
        return modifier.descriptorBlockTransformer.transformConstructor(arg)
    }

    fun getterDescriptorBlock(): CodeBlock {
        val arg = CodeBlock.of("obj.%N", declaration.simpleName.asString())
        val transformedArg = modifier.descriptorBlockTransformer.transformGetting(arg)
        val getting = transformedArg.transformIf({ type.isMarkedNullable }) {
            CodeBlock.of(
                "%T.ofNullable(%L)",
                Optional::class.asClassName(),
                it
            )
        }
        return CodeBlock.of(
            "%L.%N(%S).forGetter { obj -> %L }",
            codeCalling,
            if (type.isMarkedNullable) "optionalFieldOf" else "fieldOf",
            name,
            getting
        )
    }
}


private fun KSType.correspondCodecCalling(
    transform: (CodeBlock) -> CodeBlock = { it }
): CodeBlock {
    val cname = toClassName().toString()
    val qname = declaration.qualifiedName!!.asString()
    if (isMarkedNullable) {
        val dataType = makeNotNullable()
        return dataType.correspondCodecCalling(transform)
    }
    val codecCalling = run {
        val field = when (cname) {
            typeNameStringOf<Boolean>() -> "BOOL"
            typeNameStringOf<Byte>() -> "BYTE"
            typeNameStringOf<Short>() -> "SHORT"
            typeNameStringOf<Int>() -> "INT"
            typeNameStringOf<Long>() -> "LONG"
            typeNameStringOf<Float>() -> "FLOAT"
            typeNameStringOf<Double>() -> "DOUBLE"
            typeNameStringOf<String>() -> "STRING"
            else -> null
        } ?: return@run CodeBlock.of("%L.%L", qname, AutoCodec.DEFAULT_NAME)
        CodeBlock.of("%T.%L", TypeMirrors.Codec, field)
    }
    return transform(codecCalling)
}

