package io.github.iakakariak.chinatsu.compiler.module

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakakariak.chinatsu.annotation.AutoCodec
import io.github.iakakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakakariak.chinatsu.annotation.CodecInfo
import io.github.iakakariak.chinatsu.compiler.*

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
                    .map {
                        buildCodeBlock {
                            add("%L = ", it.name)
                            add(it.decodeBlock("buf"))
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
                    .map { it.encodeBlock("buf", "value") }
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
            val pTypeDeclaration = propertyDeclaration.type.resolve().declaration as KSClassDeclaration
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
                ?: pTypeDeclaration.correspondStreamCodecCalling()
            return PropertyInfo(
                declaration = propertyDeclaration,
                name = name,
                codecCalling = codecCalling
            )
        }
    }

    context(types: TypeMirrors)
    fun encodeBlock(bufName: String, valueName: String) =
        CodeBlock.of("%L.encode(%L, %L.%L)", codecCalling, bufName, valueName, name)


    context(types: TypeMirrors)
    fun decodeBlock(bufName: String) =
        CodeBlock.of("%L.decode(%L)", codecCalling, bufName)
}

context(types: TypeMirrors)
private fun KSClassDeclaration.correspondStreamCodecCalling(): CodeBlock {
    val qname = qualifiedName!!.asString()
    val field = when (qname) {
        qualificationOf<Boolean>() -> "BOOL"
        qualificationOf<Byte>() -> "BYTE"
        qualificationOf<Short>() -> "SHORT"
        qualificationOf<Int>() -> "INT"
        qualificationOf<Long>() -> "LONG"
        qualificationOf<Float>() -> "FLOAT"
        qualificationOf<Double>() -> "DOUBLE"
        qualificationOf<ByteArray>() -> "BYTE_ARRAY"
        qualificationOf<String>() -> "STRING_UTF8"
        else -> null
    } ?: return CodeBlock.of("%L.%L", qname, AutoStreamCodec.DEFAULT_NAME)
    return CodeBlock.of("%T.%L", types.ByteBufCodecs.toClassName(), field)
}