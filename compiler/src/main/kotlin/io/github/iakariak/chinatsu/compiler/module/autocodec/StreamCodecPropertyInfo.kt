package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakariak.chinatsu.annotation.CodecInfo
import io.github.iakariak.chinatsu.compiler.TypeMirrors
import io.github.iakariak.chinatsu.compiler.qualificationOf
import io.github.iakariak.chinatsu.compiler.typeNameStringOf
import java.util.*


internal class StreamCodecPropertyInfo(
    val declaration: KSPropertyDeclaration,
    val source: ByStreamCodec,
)  {
    companion object {
        fun fromClass(declaration: KSClassDeclaration, source: ByStreamCodec) =
            declaration.declarations
                .filterIsInstance<KSPropertyDeclaration>()
                .map { StreamCodecPropertyInfo(it, source) }

    }

    val codecInfo = declaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
    val codecCalling =
        PropertyInfo.getCodecCalling(codecInfo, declaration, source.defaultCodecName,
            KSType::correspondStreamCodecCalling
        )
    val name = PropertyInfo.getName(codecInfo, declaration)
    val type get() = declaration.type.resolve()


    fun encodeBlock(bufName: String, valueName: String): CodeBlock = context(this) {
        val type = declaration.type.resolve()
        return if (type.isMarkedNullable) {
            val arg = CodeBlock.of(
                "%T.ofNullable(%N.%L)",
                Optional::class.asClassName().parameterizedBy(type.makeNotNullable().toClassName()),
                valueName,
                name
            )
            val result = CodeBlock.of(
                "%L.encode(%N,)",
                codecCalling,
                bufName,
                arg
            )
            result
        } else {
            CodeBlock.of("%L.encode(%N, %N.%N)", codecCalling, bufName, valueName, name)
        }
    }


    fun decodeBlock(bufName: String) = if (type.isMarkedNullable) {
        CodeBlock.of("%L.decode(%N).orElse(null)", codecCalling, bufName)
    } else {
        CodeBlock.of("%L.decode(%N)", codecCalling, bufName)
    }

}



private fun KSType.correspondStreamCodecCalling(): CodeBlock {
    val cname = toClassName().toString()
    val qname = declaration.qualifiedName!!.asString()
    when {
        qname == qualificationOf<Optional<*>>() -> {
            val dataType = arguments.first().type!!.resolve()
            return CodeBlock.of("%T.optional(%L)", TypeMirrors.ByteBufCodecs, dataType.correspondStreamCodecCalling())
        }

        isMarkedNullable -> {
            val dataType = makeNotNullable()
            return CodeBlock.of(
                "%T.optional(%L)",
                TypeMirrors.ByteBufCodecs,
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
    return CodeBlock.of("%T.%L", TypeMirrors.ByteBufCodecs, field)
}

