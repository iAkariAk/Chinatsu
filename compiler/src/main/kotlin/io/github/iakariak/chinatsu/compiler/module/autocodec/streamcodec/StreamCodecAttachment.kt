package io.github.iakariak.chinatsu.compiler.module.autocodec.streamcodec

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakariak.chinatsu.compiler.Attachment
import io.github.iakariak.chinatsu.compiler.ProcessEnv


internal object StreamCodecAttachment : Attachment {
    private val attachmentName = ClassName("", "__StreamCodecAttachment_${System.nanoTime()}")
    private val attachedFunctions = mutableSetOf<FunSpec>()
    private val attachedProperties = mutableSetOf<PropertySpec>()

    fun install(funSpec: FunSpec, isExtension: Boolean = false): MemberName {
        this.attachedFunctions += funSpec
        return MemberName(attachmentName, funSpec.name, isExtension)
    }

    fun install(propertySpec: PropertySpec, isExtension: Boolean = false): MemberName {
        this.attachedProperties += propertySpec
        return MemberName(attachmentName, propertySpec.name, isExtension)
    }

    private var isAttached = false

    context(env: ProcessEnv)
    override fun attach() {
        if (isAttached) return
        val obj = TypeSpec.objectBuilder(attachmentName)
            .addFunctions(attachedFunctions)
            .addProperties(attachedProperties)
            .build()
        FileSpec.builder(attachmentName)
            .addType(obj)
            .build()
            .writeTo(env.codeGenerator, false)
        isAttached = true
    }
}