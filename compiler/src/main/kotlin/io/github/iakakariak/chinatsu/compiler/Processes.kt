package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

@DslMarker
annotation class ProcessesDsl

@ProcessesDsl
interface FileScope {
    fun notifyChange(file: KSFile)
    fun TypeSpec.Builder.attachNotifies(): TypeSpec.Builder
}


class ProcessEnv(
    val environment: SymbolProcessorEnvironment,
    val resolver: Resolver
) {
    fun createFile(block: FileScope.() -> Unit) {
        val scope = object : FileScope {
            val files = mutableListOf<KSFile>()
            override fun notifyChange(file: KSFile) {
                files.add(file)
            }

            override fun TypeSpec.Builder.attachNotifies() = apply {
                files.forEach(::addOriginatingKSFile)
            }
        }
        scope.apply(block)
    }

}