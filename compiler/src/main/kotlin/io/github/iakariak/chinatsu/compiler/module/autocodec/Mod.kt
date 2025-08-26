package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakariak.chinatsu.annotation.AutoCodec
import io.github.iakariak.chinatsu.annotation.AutoStreamCodec
import io.github.iakariak.chinatsu.compiler.*
import io.github.iakariak.chinatsu.compiler.module.autocodec.codec.ByCodec
import io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec.ByStreamCodec

// P: Parameter
internal const val P_BUF_NAME = "buf"
internal const val P_VALUE_NAME = "value"

context(env: ProcessEnv)
internal fun TypeMirrors.generateCodecs() = env.createFile {
    val codecClasses = env.resolver.getSymbolsWithAnnotation<AutoCodec>()
        .filterIsInstance<KSClassDeclaration>()
        .map { ByCodec(it, it.getAnnotationsByType(AutoCodec::class).first().name) }
    val autoCodecClasses = env.resolver.getSymbolsWithAnnotation<AutoStreamCodec>()
        .filterIsInstance<KSClassDeclaration>()
        .map { ByStreamCodec(it, it.getAnnotationsByType(AutoStreamCodec::class).first().name) }
    (codecClasses + autoCodecClasses)
        .groupBy { byCodec -> byCodec.declaration.containingFile }
        .filterKeys { it != null }
        .forEach { (file, byCodecs) -> generateForAnnotatedByCodec(file!!, byCodecs) }
}

context(env: ProcessEnv)
private fun NotifyScope.generateForAnnotatedByCodec(source: KSFile, byCodecs: List<AnnotatedByCodec>) {
    notifyChange(source)
    val properties =
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

    FileSpec.builder(source.packageName.asString(), source.fileNameWithoutExtension + "_Codecs")
        .apply { properties.forEach(::addProperty) }
        .addAliasedImport(TypeMirrors.JFunction, "JFunction")
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "RedundantVisibilityModifier")
                .addMember("%S", "MoveLambdaOutsideParentheses")
                .addMember("%S", "RedundantSamConstructor")
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        .build()
        .writeTo(env.codeGenerator, false)
}

internal interface AnnotatedByCodec {
    val declaration: KSClassDeclaration
    val name: String

    val defaultCodecName: String

    val qualifiedName
        get() = name.replace("~", declaration.simpleName.asString())

    fun typeOf(type: TypeName): ParameterizedTypeName

    context(env: ProcessEnv)
    fun generateCodeBlock(): Pair<ParameterizedTypeName, Any> // codec-type to formattable-code(kotlinpoet)
}



