package io.github.iakariak.chinatsu.compiler.module.config

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import io.github.iakariak.chinatsu.annotation.CConfigs
import io.github.iakariak.chinatsu.compiler.*

private const val MODULE_NAME = "config"

context(env: ProcessEnv)
fun ChinatsuAppSetupRegisterScope.registerConfig() {
    env.resolver.getSymbolsWithAnnotation(annotation<CConfigs>())
        .filterIsInstance<KSClassDeclaration>()
        .filter {
            (it.classKind == ClassKind.OBJECT)
                .onFalse { env.logger.error("The configs must be a object") }
        }
        .filter {
            TypeMirrors.Configs.resolve().asStarProjectedType().isAssignableFrom(it.asStarProjectedType())
                .onFalse { env.logger.error("Your config cannot extends class Configs") }
        }
        .forEach {
            it.containingFile?.let { file -> notifyChange(file) }
            addCommon(MODULE_NAME, CodeBlock.of("%T.get()", it.toClassName()))
        }
}
