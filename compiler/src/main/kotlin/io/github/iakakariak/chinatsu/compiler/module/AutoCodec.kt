package io.github.iakakariak.chinatsu.compiler.module

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakakariak.chinatsu.annotation.AutoCodec
import io.github.iakakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakakariak.chinatsu.annotation.CodecInfo
import io.github.iakakariak.chinatsu.compiler.*
import java.util.*

context(env: ProcessEnv)
fun TypeMirrors.generateCodecs() = env.createFile {
    val codecClass = env.resolver.getSymbolsWithAnnotation(annotation<AutoCodec>())
        .filterIsInstance<KSClassDeclaration>()
        .map { ByCodec(it, it.getAnnotationsByType(AutoCodec::class).first().name) }
    val autoCodecClass = env.resolver.getSymbolsWithAnnotation(annotation<AutoStreamCodec>())
        .filterIsInstance<KSClassDeclaration>()
        .map { ByStreamCodec(it, it.getAnnotationsByType(AutoStreamCodec::class).first().name) }
    (codecClass + autoCodecClass)
        .groupBy { byCodec -> byCodec.declaration.containingFile }
        .filterKeys { it != null }
        .forEach { (file, byCodecs) -> generateForAnnotatedByCodec(file!!, byCodecs) }
}

context(env: ProcessEnv)
private fun NotifyScope.generateForAnnotatedByCodec(source: KSFile, byCodecs: List<AnnotatedByCodec>) {
    notifyChange(source)
    val properties = context(env.typeMirrors) {
        byCodecs.flatMap { byCodec ->
            val pName = byCodec.qualifiedName
            val pImplName = "c_impl_$pName"
            val (codecType, codecInitializer) = byCodec.generateCodeBlock()
            val impl = PropertySpec.builder(
                name = pImplName,
                type = codecType,
                KModifier.PRIVATE
            )
                .initializer("%L", codecInitializer)
                .build()
            val wrapper = PropertySpec.builder(
                name = pName,
                type = codecType,
                KModifier.PUBLIC
            )
                .receiver(
                    byCodec.declaration.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .find { it.isCompanionObject }
                        ?.toClassName()
                        .onNull { env.logger.error("Merely when you had a manually-created companion object can we generate $pName for you") }
                )
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %L", pImplName)
                        .build()
                )
                .build()
            listOf(impl, wrapper)
        }
    }
    FileSpec.builder(source.packageName.asString(), source.fileNameWithoutExtension + "_Codecs")
        .apply { properties.forEach(::addProperty) }
        .build()
        .writeTo(env.codeGenerator, false)
}

private sealed interface AnnotatedByCodec {
    val declaration: KSClassDeclaration
    val name: String

    val qualifiedName
        get() = name.replace("~", declaration.simpleName.asString())

    context(types: TypeMirrors)
    fun type(type: TypeName): ParameterizedTypeName = when (this) {
        is ByCodec -> types.Codec.toClassName()
            .parameterizedBy(type)

        is ByStreamCodec -> types.StreamCodec.toClassName()
            .parameterizedBy(types.FriendlyByteBuf.toClassName(), type)
    }

    context(env: ProcessEnv, types: TypeMirrors)
    fun generateCodeBlock(): Pair<ParameterizedTypeName, TypeSpec>
}

data class ByCodec(override val declaration: KSClassDeclaration, override val name: String) : AnnotatedByCodec {
    context(env: ProcessEnv, types: TypeMirrors)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, TypeSpec> {
        TODO()
    }

}

data class ByStreamCodec(override val declaration: KSClassDeclaration, override val name: String) :
    AnnotatedByCodec {
    context(env: ProcessEnv, types: TypeMirrors)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, TypeSpec> {
        val tType = declaration.toClassName()
        val type = type(tType)
        val decode = FunSpec.builder("decode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("buf", types.FriendlyByteBuf.toClassName())
            .returns(tType)
            .addCode("return %T", tType)
            .addCode(
                declaration.declarations
                    .filterIsInstance<KSPropertyDeclaration>()
                    .map { PropertyInfo.from(it, this@ByStreamCodec) }
                    .map { it ->
                        buildCodeBlock {
                            val pType = it.declaration.type.resolve()
                            add("%L = ", it.name)
                            add(it.decodeBlock(pType, "buf"))
                        }
                    }
                    .toList()
                    .joinToCode(",\n", prefix = "(\n", suffix = "\n)")
            )
            .build()
        val encode = FunSpec.builder("encode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("buf", types.FriendlyByteBuf.toClassName())
            .addParameter("value", tType)
            .addCode(
                declaration.declarations
                    .filterIsInstance<KSPropertyDeclaration>()
                    .map { PropertyInfo.from(it, this@ByStreamCodec) }
                    .map {
                        val pType = it.declaration.type.resolve()
                        it.encodeBlock(pType, "buf", "value")
                    }
                    .toList()
                    .joinToCode("\n")
            )
            .build()
        val initializer = TypeSpec.anonymousClassBuilder()
            .addSuperinterface(type)
            .addFunction(decode)
            .addFunction(encode)
            .build()
        return type to initializer
    }
}


private class PropertyInfo private constructor(
    val declaration: KSPropertyDeclaration,
    val name: String,
    val codecCalling: CodeBlock
) {
    companion object {
        context(types: TypeMirrors)
        fun from(propertyDeclaration: KSPropertyDeclaration, codec: AnnotatedByCodec): PropertyInfo {
            val codecInfo = propertyDeclaration.getAnnotationsByType(CodecInfo::class).firstOrNull()
            val pType = propertyDeclaration.type.resolve()
            val pTypeDeclaration = pType.declaration as KSClassDeclaration
            val pName = propertyDeclaration.simpleName.asString()
            val ptqName = pTypeDeclaration.qualifiedName!!.asString()
            val codecDefaultName = when (codec) {
                is ByCodec -> AutoCodec.DEFAULT_NAME
                is ByStreamCodec -> AutoStreamCodec.DEFAULT_NAME
            }
            val name = codecInfo?.name?.replace("~", pName) ?: pName
            val codecCalling = codecInfo?.codecCalling
                ?.replace("~", ptqName)
                ?.replace("^", codecDefaultName)
                ?.let(CodeBlock::of)
                ?: pType.correspondStreamCodecCalling()
            return PropertyInfo(
                declaration = propertyDeclaration,
                name = name,
                codecCalling = codecCalling
            )
        }
    }

    context(types: TypeMirrors)
    fun encodeBlock(type: KSType, bufName: String, valueName: String): CodeBlock = if (type.isMarkedNullable)
        CodeBlock.of(
            "%L.encode(%L, %T.ofNullable(%L.%L))",
            codecCalling,
            bufName,
            Optional::class.asClassName().parameterizedBy(type.makeNotNullable().toClassName()),
            valueName,
            name
        )
    else
        CodeBlock.of("%L.encode(%L, %L.%L)", codecCalling, bufName, valueName, name)


    context(types: TypeMirrors)
    fun decodeBlock(type: KSType, bufName: String) = if (type.isMarkedNullable)
        CodeBlock.of("%L.decode(%L).orElse(null)", codecCalling, bufName)
    else
        CodeBlock.of("%L.decode(%L)", codecCalling, bufName)

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
        qualificationOf<Boolean>() -> "BOOL"
        qualificationOf<Byte>() -> "BYTE"
        qualificationOf<Short>() -> "SHORT"
        qualificationOf<Int>() -> "INT"
        qualificationOf<Long>() -> "LONG"
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
    } ?: return CodeBlock.of("%L.%L", qname, AutoStreamCodec.DEFAULT_NAME)
    return CodeBlock.of("%T.%L", types.ByteBufCodecs.toClassName(), field)
}

