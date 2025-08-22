package io.github.iakariak.chinatsu.compiler.module.autocodec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import io.github.iakariak.chinatsu.annotation.CodecInfo


internal object PropertyInfo {
    fun getCodecCalling(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
        codecDefaultName: String,
        feedback: (propertyType: KSType) -> CodeBlock,
    ): CodeBlock {
        val pType = declaration.type.resolve()
        val pTypeDeclaration = pType.declaration as KSClassDeclaration
        val ptqName = pTypeDeclaration.qualifiedName!!.asString()

        return codecInfo?.codecCalling
            ?.replace("~", ptqName)
            ?.replace("^", codecDefaultName)
            ?.let(CodeBlock::of) ?: feedback(pType)
    }

    fun getName(
        codecInfo: CodecInfo?,
        declaration: KSPropertyDeclaration,
    ): String {
        val pName = declaration.simpleName.asString()
        return codecInfo?.name?.replace("~", pName) ?: pName
    }
}
