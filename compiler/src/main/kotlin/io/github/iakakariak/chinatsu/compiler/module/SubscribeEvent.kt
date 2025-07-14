package io.github.iakakariak.chinatsu.compiler.module

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import io.github.iakakariak.chinatsu.annotation.SubscribeEvent
import io.github.iakakariak.chinatsu.compiler.*


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
    val listener = declaration.toMemberName()
    return CodeBlock.of("%L.register(%L)", annotation.registry, listener.reference())
}
