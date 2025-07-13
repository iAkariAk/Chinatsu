package io.github.iakakariak.chinatsu.compiler.module

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import io.github.iakakariak.chinatsu.annotation.SubscribeEvent
import io.github.iakakariak.chinatsu.compiler.ChinatsuAppSetupRegisterScope
import io.github.iakakariak.chinatsu.compiler.ProcessEnv
import io.github.iakakariak.chinatsu.compiler.annotation
import io.github.iakakariak.chinatsu.compiler.onFalse


context(env: ProcessEnv)
fun ChinatsuAppSetupRegisterScope.registerSubscribeEvent() {
    println(env.resolver.getSymbolsWithAnnotation(annotation<SubscribeEvent>()))
    env.resolver.getSymbolsWithAnnotation(annotation<SubscribeEvent>())
        .filterIsInstance<KSFunctionDeclaration>()
        .map { it.getAnnotationsByType(SubscribeEvent::class).first() to it }
        .filter { (_, d) ->
            ((d.parent as? KSClassDeclaration)?.classKind?.let { it == ClassKind.OBJECT } ?: true)
                .onFalse { env.logger.error("You must ensure what you annotation can be invoked statically.") }
        }
        .forEach { (annotation, declaration) ->
            declaration.containingFile?.let { file -> notifyChange(file) }
            add(annotation.side, "SubscribeEvent", registerEventBlock(declaration, annotation))
        }
}

context(env: ProcessEnv)
private fun registerEventBlock(declaration: KSFunctionDeclaration, annotation: SubscribeEvent): CodeBlock {
    val isTopLevel = declaration.parent as? KSClassDeclaration == null
    val listener = if (isTopLevel) MemberName(declaration.packageName.asString(), declaration.simpleName.asString())
    else MemberName(declaration.parentDeclaration!!.simpleName.asString(), declaration.simpleName.asString())
    return CodeBlock.of("%L.register(%L)", annotation.registry, listener.reference())
}
