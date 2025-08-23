package io.github.iakariak.chinatsu.compiler.hiddenapi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakariak.chinatsu.compiler.ProcessEnv
import io.github.iakariak.chinatsu.compiler.TypeMirrors


internal object HiddenApiAccessor : Registry() { /*...*/}

private var generated = false

context(env: ProcessEnv)
fun TypeMirrors.generateHiddenApiAccessor() {
    if (generated) return
    val accessor = HiddenApiAccessor.generateAccessor()
    FileSpec.builder(HIDDEN_API)
        .addType(accessor)
        .addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "ClassName")
                .addMember("%S", "FunctionName")
                .addMember("%S", "RemoveRedundantQualifierName")
                .addMember("%S", "RedundantVisibilityModifier")
                .addMember("%S", "SpellCheckingInspection")
                .addMember("%S", "ObjectPropertyName")
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        .build()
        .writeTo(env.codeGenerator, true)
    generated = true
}