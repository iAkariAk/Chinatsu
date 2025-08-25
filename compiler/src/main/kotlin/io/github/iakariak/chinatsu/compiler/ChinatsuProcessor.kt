@file:OptIn(KspExperimental::class)

package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import io.github.iakariak.chinatsu.compiler.hiddenapi.generateHiddenApiAccessor
import io.github.iakariak.chinatsu.compiler.module.autocodec.generateCodecs

class ChinatsuProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = ChinatsuProcessor(environment)
}

class ChinatsuProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val env = ProcessEnv(environment, resolver)
        with(TypeMirrors) {
            context(env) {
                generateHiddenApiAccessor()
                generateChinatsuApp()
                generateCodecs()

                env.attachments.forEach {
                    it.attach()
                }
            }
        }

        return emptyList()
    }

}
