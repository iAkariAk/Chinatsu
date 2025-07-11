@file:OptIn(KspExperimental::class)

package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated

class ChinatsuProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = ChinatsuProcessor(environment)
}

class ChinatsuProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val env = ProcessEnv(environment, resolver)
        val mirrors = TypeMirrors(resolver)
        with(mirrors) {
            context(env) {
                generateChinatsuApp()
            }
        }

        return emptyList()
    }

}
