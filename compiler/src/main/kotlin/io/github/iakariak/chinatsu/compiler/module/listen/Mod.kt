package io.github.iakariak.chinatsu.compiler.module.listen

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.Listen
import io.github.iakariak.chinatsu.compiler.*


context(env: ProcessEnv)
fun ChinatsuAppSetupRegisterScope.registerEventListen() {
    env.resolver.getSymbolsWithAnnotation<Listen>()
        .filterIsInstance<KSFunctionDeclaration>()
        .map { it.getAnnotationsByType(Listen::class).first() to it }
        .filter { (_, d) ->
            ((d.parent as? KSClassDeclaration)?.classKind?.let { it == ClassKind.OBJECT } ?: true)
                .onFalse { env.logger.error("You must ensure what you annotation can be invoked statically.") }
        }
        .forEach { (annotation, declaration) ->
            declaration.containingFile?.let { file -> notifyChange(file) }
            add(annotation.side, "EventListen", registerEventBlock(declaration, annotation))
        }
}

context(env: ProcessEnv)
private fun registerEventBlock(declaration: KSFunctionDeclaration, annotation: Listen): CodeBlock {
    val listener = declaration.toMemberName()
    return CodeBlock.of("%L.register(%L)", annotation.registry, listener.reference())
}
