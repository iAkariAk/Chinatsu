package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.toMemberName
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

    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val codecCalling =
        PropertyInfo.getCodecCalling(codecInfo, declaration, source.defaultCodecName, KSType::correspondCodecCalling)
    val name = PropertyInfo.getName(codecInfo, declaration)
    val type get() = declaration.type.resolve()

    fun descriptorBlock(): CodeBlock {
        val getter = if (type.isMarkedNullable) {
            CodeBlock.of(
                "{ obj -> %T.ofNullable(obj.%N) }",
                Optional::class.asClassName(),
                declaration.simpleName.asString()
            )
        } else {
            declaration.toMemberName().reference()
        }
        return CodeBlock.of(
            "%L.%N(%S).forGetter(%L)",
            codecCalling,
            if (type.isMarkedNullable) "optionalFieldOf" else "fieldOf",
            name,
            getter
        )
    }
}


private fun KSType.correspondCodecCalling(): CodeBlock {
    val cname = toClassName().toString()
    val qname = declaration.qualifiedName!!.asString()
    if (isMarkedNullable) {
        val dataType = makeNotNullable()
        return dataType.correspondCodecCalling()
    }
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
    } ?: return CodeBlock.of("%L.%L", qname, AutoCodec.DEFAULT_NAME)
    return CodeBlock.of("%T.%L", TypeMirrors.Codec, field)
}

