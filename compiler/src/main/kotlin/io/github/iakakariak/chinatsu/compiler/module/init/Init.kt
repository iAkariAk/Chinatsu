package io.github.iakakariak.chinatsu.compiler.module.init

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakakariak.chinatsu.annotation.Init
import io.github.iakakariak.chinatsu.annotation.SideType
import io.github.iakakariak.chinatsu.compiler.*

private const val MODULE_NAME = "init"

context(env: ProcessEnv)
fun ChinatsuAppSetupRegisterScope.registerInit() {
    val inits = generateSideInits()
    fun specifyInits(sideType: SideType) = inits
        .filter { (side, _) -> side == sideType }
        .map { (_, block) -> block }
        .takeIf { it.isNotEmpty() }
        ?.joinToCode("\n")

    fun registerInits(sideType: SideType) = specifyInits(sideType)?.let {
        add(sideType, MODULE_NAME, it)
    }

    SideType.entries.forEach(::registerInits)
}

context(env: ProcessEnv)
private fun NotifyScope.generateSideInits(): List<Pair<SideType, CodeBlock>> {
    val inits = env.resolver.getSymbolsWithAnnotation(annotation<Init>()).map {
        it.getAnnotationsByType(Init::class).first().side to it
    }.mapNotNull { (side, declaration) ->
        declaration.containingFile?.let { notifyChange(it) }
        // import is tricky when conflicting, henceforward we use entire name
        side to when (declaration) {
            is KSFile -> CodeBlock.of(
                "Class.forName(%S)",
                ClassName(declaration.packageName.asString(), declaration.jvmName)
            )

            is KSClassDeclaration -> CodeBlock.of(
                "Class.forName(%1L::class.java.name, true, %1L::class.java.classLoader)",
                declaration.toClassName()
            )

            is KSPropertyDeclaration -> CodeBlock.of(
                "%L", declaration.absolutePath
            )

            is KSFunctionDeclaration -> {
                if (declaration.parameters.isNotEmpty()) {
                    env.logger.error("Only an empty args function can it be initialized")
                    return@mapNotNull null
                }

                CodeBlock.of(
                    "%L()", declaration.absolutePath
                )
            }

            else -> {
                env.logger.error("Unsupported target")
                return@mapNotNull null
            }
        }
    }.toList()
    return inits
}