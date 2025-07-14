package io.github.iakakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakakariak.chinatsu.annotation.AutoCodec
import io.github.iakakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakakariak.chinatsu.annotation.CodecInfo
import io.github.iakakariak.chinatsu.compiler.TypeMirrors
import io.github.iakakariak.chinatsu.compiler.module.autocodec.PropertyInfo.Companion.getCodecCalling
import io.github.iakakariak.chinatsu.compiler.module.autocodec.PropertyInfo.Companion.getName
import io.github.iakakariak.chinatsu.compiler.qualificationOf
import io.github.iakakariak.chinatsu.compiler.toMemberName
import io.github.iakakariak.chinatsu.compiler.typeNameStringOf
import java.util.*

context(types: TypeMirrors)
internal fun StreamCodecPropertyInfo(
    declaration: KSPropertyDeclaration,
    source: ByStreamCodec
): StreamCodecPropertyInfo = object : StreamCodecPropertyInfo,
    PropertyInfo<ByStreamCodec> by BasicPropertyInfo(declaration, source, { pType ->
        pType.correspondStreamCodecCalling()
    }) {}

internal interface StreamCodecPropertyInfo : PropertyInfo<ByStreamCodec> {
    companion object {
        context(types: TypeMirrors)
        fun fromClass(declaration: KSClassDeclaration, source: ByStreamCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { StreamCodecPropertyInfo(it, source) }

    }

    fun encodeBlock(bufName: String, valueName: String): CodeBlock {
        val type = declaration.type.resolve()
        return if (type.isMarkedNullable) {
            CodeBlock.of(
                "%L.encode(%L, %T.ofNullable(%L.%L))",
                codecCalling,
                bufName,
                Optional::class.asClassName().parameterizedBy(type.makeNotNullable().toClassName()),
                valueName,
                name
            )
        } else {
            CodeBlock.of("%L.encode(%L, %L.%L)", codecCalling, bufName, valueName, name)
        }
    }


    fun decodeBlock(bufName: String) = if (type.isMarkedNullable) {
        CodeBlock.of("%L.decode(%L).orElse(null)", codecCalling, bufName)
    } else {
        CodeBlock.of("%L.decode(%L)", codecCalling, bufName)
    }


}

context(types: TypeMirrors)
internal fun CodecPropertyInfo(
    declaration: KSPropertyDeclaration,
    source: ByCodec
): CodecPropertyInfo = object : CodecPropertyInfo,
    PropertyInfo<ByCodec> by BasicPropertyInfo(declaration, source, {
        it.correspondCodecCalling()
    }) {}

internal interface CodecPropertyInfo : PropertyInfo<ByCodec> {
    companion object {
        context(types: TypeMirrors)
        fun fromClass(declaration: KSClassDeclaration, source: ByCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { CodecPropertyInfo(it, source) }

    }

    fun descriptorBlock() = CodeBlock.of(
        "%L.%L(%S).forGetter(%L)",
        codecCalling,
        if (type.isMarkedNullable) "optionalFieldOf" else "fieldOf",
        name,
        declaration.toMemberName().reference()
    )
}


context(types: TypeMirrors)
private fun <T : AnnotatedByCodec> BasicPropertyInfo(
    declaration: KSPropertyDeclaration,
    source: T,
    codecCallingFeedback: (propertyType: KSType) -> CodeBlock
): PropertyInfo<T> {
    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    return object : PropertyInfo<T> {
        override val source = source
        override val codecCalling =
            getCodecCalling(codecInfo, declaration, source.defaultCodecName, codecCallingFeedback)
        override val name = getName(codecInfo, declaration)
        override val declaration = declaration
        override val types = types
    }
}

internal interface PropertyInfo<T : AnnotatedByCodec> {
    val types: TypeMirrors
    val declaration: KSPropertyDeclaration
    val name: String
    val codecCalling: CodeBlock
    val source: T

    val type get() = declaration.type.resolve()

    companion object {
        context(types: TypeMirrors)
        fun getCodecCalling(
            codecInfo: CodecInfo?,
            declaration: KSPropertyDeclaration,
            codecDefaultName: String,
            feedback: (propertyType: KSType) -> CodeBlock
        ): CodeBlock {
            val pType = declaration.type.resolve()
            val pTypeDeclaration = pType.declaration as KSClassDeclaration
            val ptqName = pTypeDeclaration.qualifiedName!!.asString()

            return codecInfo?.codecCalling
                ?.replace("~", ptqName)
                ?.replace("^", codecDefaultName)
                ?.let(CodeBlock::of) ?: feedback(pType)
        }

        fun getName(
            codecInfo: CodecInfo?,
            declaration: KSPropertyDeclaration,
        ): String {
            val pName = declaration.simpleName.asString()
            return codecInfo?.name?.replace("~", pName) ?: pName
        }
    }
}

context(types: TypeMirrors)
private fun KSType.correspondStreamCodecCalling(): CodeBlock {
    val cname = toClassName().toString()
    val qname = declaration.qualifiedName!!.asString()
    when {
        qname == qualificationOf<Optional<*>>() -> {
            val dataType = arguments.first().type!!.resolve()
            return CodeBlock.of("%T.optional(%L)", types.ByteBufCodecs, dataType.correspondStreamCodecCalling())
        }

        isMarkedNullable -> {
            val dataType = makeNotNullable()
            return CodeBlock.of(
                "%T.optional(%L)",
                types.ByteBufCodecs.toClassName(),
                dataType.correspondStreamCodecCalling()
            )
        }
    }
    val field = when (cname) {
        typeNameStringOf<Boolean>() -> "BOOL"
        typeNameStringOf<Byte>() -> "BYTE"
        typeNameStringOf<Short>() -> "SHORT"
        typeNameStringOf<Int>() -> "INT"
        typeNameStringOf<Long>() -> "LONG"
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
    } ?: return CodeBlock.of("%L.%L", qname, AutoStreamCodec.DEFAULT_NAME)
    return CodeBlock.of("%T.%L", types.ByteBufCodecs.toClassName(), field)
}


context(types: TypeMirrors)
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
    return CodeBlock.of("%T.%L", types.Codec.toClassName(), field)
}

