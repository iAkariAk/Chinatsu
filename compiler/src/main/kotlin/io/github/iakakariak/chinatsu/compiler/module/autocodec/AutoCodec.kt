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
import java.util.*

private val JFunctionName = java.util.function.Function::class.asClassName()

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
        .addAliasedImport(JFunctionName, "JFunction")
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "RedundantVisibilityModifier")
                .addMember("%S", "MoveLambdaOutsideParentheses")
                .addMember("%S", "RedundantSamConstructor")
                .build()
        )
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
        val infos = CodecPropertyInfo.fromClass(declaration, this@ByCodec).toList()
        val infosReversed = infos.reversed()

        fun CodeBlock.Builder.addCurryConstructor(index: Int = 0) {
            if (index >= infos.size) {
                val args = infos
                    .mapIndexed { i, info -> "a$i" to info }
                    .joinToCode { (name, info) ->
                        buildCodeBlock {
                            add(name)
                            if (info.type.isMarkedNullable) {
                                add(".orElse(null)")
                            }
                        }
                    }
                add("%T(%L)\n", declaration.toClassName(), args)
                return
            }

            val info = infos[index]
            val argType = if (info.type.isMarkedNullable) {
                val t = info.type.makeNotNullable().toClassName()
                Optional::class.asClassName().parameterizedBy(t)
            } else {
                info.type.toClassName()
            }

            add("%T { a$index: %T ->\n", JFunctionName, argType)
            indent()
            addCurryConstructor(index + 1)
            unindent()
            add("}")
            if (index >= 1) {
                add("\n")
            }
        }

        fun CodeBlock.Builder.addCurryApRecursively(index: Int = 0) {
            if (index >= infosReversed.size) return

            val info = infosReversed[index]
            add("instance.ap(\n")
            indent()
            if (index == infosReversed.lastIndex) {
                addCurryConstructor()
            }
            addCurryApRecursively(index + 1)
            add(",\n")
            add(info.descriptorBlock())
            add("\n")
            unindent()
            add(")")
            if (index == 0) {
                add("\n")
            }
        }


        val initializer = buildCodeBlock {
            add("%T.create { instance ->\n", env.typeMirrors.RecordCodecBuilder.toClassName())
            addCurryApRecursively()
            add("}")
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
                            add("%N = ", it.name)
                            add(it.decodeBlock("buf"))
                        }
                    }
                    .toList()
                    .joinToCode(",\n", prefix = "(\n⇥", suffix = "⇤\n)")
            )
            .build()
        val encode = FunSpec.builder("encode")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("buf", types.FriendlyByteBuf.toClassName())
            .addParameter("value", tType)
            .addCode(
                StreamCodecPropertyInfo.fromClass(declaration, this)
                    .map {
                        it.encodeBlock("buf", "value")
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


