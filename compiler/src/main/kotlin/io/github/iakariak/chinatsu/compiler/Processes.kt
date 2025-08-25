package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

@DslMarker
annotation class ProcessesDsl

interface NotifyScope {
    fun notifyChange(file: KSFile)
    fun TypeSpec.Builder.attachNotifies(): TypeSpec.Builder
}

fun NotifyScope(): NotifyScope = NotifyScopeImpl()

private class NotifyScopeImpl : NotifyScope{
    val files = mutableListOf<KSFile>()
    override fun notifyChange(file: KSFile) {
        files.add(file)
    }

    override fun TypeSpec.Builder.attachNotifies() = apply {
        files.forEach(::addOriginatingKSFile)
    }
}


interface Attachment {
    context(env: ProcessEnv)
    fun attach()
}


class ProcessEnv(
    val environment: SymbolProcessorEnvironment,
    val resolver: Resolver,
) {
    val codeGenerator get() = environment.codeGenerator
    val logger get() = environment.logger
    private val _attachments = mutableSetOf<Attachment>()
    val attachments = _attachments as Set<Attachment>

    fun createFile(block: NotifyScope.() -> Unit) {
        val scope = object : NotifyScope {
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

    fun attach(attachment: Attachment) {
        _attachments.add(attachment)
    }
}