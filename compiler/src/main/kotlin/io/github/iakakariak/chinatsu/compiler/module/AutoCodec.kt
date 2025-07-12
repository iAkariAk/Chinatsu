package io.github.iakakariak.chinatsu.compiler.module

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
        .map { AnnotatedByCodec.Codec(it, it.getAnnotationsByType(AutoCodec::class).first().name) }
    val autoCodecClass = env.resolver.getSymbolsWithAnnotation(annotation<AutoStreamCodec>())
        .filterIsInstance<KSClassDeclaration>()
        .map { AnnotatedByCodec.StreamCodec(it, it.getAnnotationsByType(AutoStreamCodec::class).first().name) }
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
            val codecType = byCodec.type(byCodec.declaration.toClassName())
            val impl = PropertySpec.builder(
                name = pImplName,
                type = codecType,
                KModifier.PRIVATE
            )
                .initializer("TODO(%L)", "CODEC") // todo: complete it
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
        is Codec -> types.Codec.toClassName()
            .parameterizedBy(type)

        is StreamCodec -> types.StreamCodec.toClassName()
            .parameterizedBy(types.FriendlyByteBuf.toClassName(), type)
    }

    data class Codec(override val declaration: KSClassDeclaration, override val name: String) : AnnotatedByCodec
    data class StreamCodec(override val declaration: KSClassDeclaration, override val name: String) : AnnotatedByCodec
}
