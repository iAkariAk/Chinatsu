package io.github.iakakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakakariak.chinatsu.annotation.AutoCodec
import io.github.iakakariak.chinatsu.annotation.AutoStreamCodec
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

internal sealed interface AnnotatedByCodec {
    val declaration: KSClassDeclaration
    val name: String

    val defaultCodecName
        get() = when (this) {
            is ByCodec -> AutoCodec.DEFAULT_NAME
            is ByStreamCodec -> AutoStreamCodec.DEFAULT_NAME
        }

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
    fun generateCodeBlock(): Pair<ParameterizedTypeName, Any>
}

internal data class ByCodec(
    override val declaration: KSClassDeclaration,
    override val name: String
) : AnnotatedByCodec {
    context(env: ProcessEnv, types: TypeMirrors)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, Any> {
        val tType = declaration.toClassName()
        val type = type(tType)

        fun apN(infos: List<CodecPropertyInfo>, constructorRef: CodeBlock) = buildCodeBlock {
            val n = infos.size
            if (n <= 16) {
                add("instance.ap")
                if (n != 1) {
                    add("$n")
                }
                add("(")
                indent()
                add("instance.point(%L),\n", constructorRef)
                add(infos.joinToCode(",\n", suffix = "\n") {
                    it.descriptorBlock()
                })
                unindent()
                add(")")
            }
        }


        val initializer = buildCodeBlock {
            add(
                "%T.create { instance ->\n", env.typeMirrors.RecordCodecBuilder.toClassName()
            )
            indent()
            val infos = CodecPropertyInfo.fromClass(declaration, this@ByCodec).toList()
            val constructorRef = declaration.toClassName().constructorReference()
            add(apN(infos,constructorRef))
            add("}")
            unindent()
        }
        return type to initializer
    }
}


internal data class ByStreamCodec(override val declaration: KSClassDeclaration, override val name: String) :
    AnnotatedByCodec {
    context(env: ProcessEnv, types: TypeMirrors)
    override fun generateCodeBlock(): Pair<ParameterizedTypeName, Any> {
        val tType = declaration.toClassName()
        val type = type(tType)
        val decode = FunSpec.builder("decode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("buf", types.FriendlyByteBuf.toClassName())
            .returns(tType)
            .addCode("return %T", tType)
            .addCode(
                StreamCodecPropertyInfo.fromClass(declaration, this)
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
                StreamCodecPropertyInfo.fromClass(declaration, this)
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


