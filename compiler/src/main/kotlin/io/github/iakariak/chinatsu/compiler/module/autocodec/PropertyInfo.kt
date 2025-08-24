package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.CodecInfo
import kotlin.reflect.KClass


internal object PropertyInfo {
    fun getCodecCalling(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
        codecDefaultName: String,
        feedback: (propertyType: KSType) -> CodeBlock,
    ): CodeBlock {
        val type = declaration.type
        val rType = type.resolve()
        val rTypeDeclaration = rType.declaration as KSClassDeclaration
        val rqName = rTypeDeclaration.qualifiedName!!.asString()

        return codecInfo?.codecCalling
            ?.replace("~", rqName)
            ?.replace("^", codecDefaultName)
            ?.let(CodeBlock::of) ?: feedback(rType)
    }

    fun getName(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
    ): String {
        val pName = declaration.simpleName.asString()
        return codecInfo?.name?.replace("~", pName) ?: pName
    }

    fun <T> scanModifiers(annotated: KSAnnotated, builtinModifiers: Map<KClass<out Annotation>, (Annotation) -> T>) = builtinModifiers.mapNotNull{
        (annotationClass, factory) ->
        annotated.getAnnotationsByType(annotationClass).firstOrNull()?.let { annotation ->
            factory(annotation)
        }
    }
}
