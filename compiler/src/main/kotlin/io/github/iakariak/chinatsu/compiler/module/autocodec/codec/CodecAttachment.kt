package io.github.iakariak.chinatsu.compiler.module.autocodec.codec

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakariak.chinatsu.compiler.Attachment
import io.github.iakariak.chinatsu.compiler.ProcessEnv
import io.github.iakariak.chinatsu.compiler.TypeMirrors

object CodecAttachment : Attachment {
    private val attachmentName = ClassName("", "__CodecAttachment_${System.nanoTime()}")
    private var maxN = 0

    fun requireN(n: Int) {
        require(n in 1..256)
        maxN = maxOf(n, maxN)
    }

    fun ap(n: Int, app: CodeBlock, pointed: CodeBlock, args: List<CodeBlock>): CodeBlock {
        requireN(n)
        return CodeBlock.of("%T.ap$n(\n⇥%L,\n%L,\n%L⇤\n)\n", attachmentName, app, pointed, args.joinToCode(", \n"))
    }

    private var isAttached = false

    context(env: ProcessEnv)
    override fun attach() {
        if (isAttached) return
        val obj = TypeSpec.objectBuilder(attachmentName)
            .addAps(maxN)
            .build()
        FileSpec.builder(attachmentName)
            .addType(obj)
            .build()
            .writeTo(env.codeGenerator, false)
        isAttached = true
    }
}


private fun TypeSpec.Builder.addAps(n: Int) = apply {
    val F = TypeVariableName("F", TypeMirrors.DFK1)
    val Mu = TypeVariableName("Mu", TypeMirrors.DFApplicativeMu)

    fun appTypeOf(type: TypeName) = TypeMirrors.DFApp.parameterizedBy(F, type)

    val R = TypeVariableName("R")
    for (i in 1..n) {
        val PTypes = List(i) { TypeVariableName("P${it + 1}") }
        val params = PTypes.mapIndexed { index, type ->
            ParameterSpec.builder("p${index + 1}", type).build()
        }
        val applicativeType = TypeMirrors.DFApplicative.parameterizedBy(F, Mu)
        val appFuncType = LambdaTypeName.get(
            parameters = params,
            returnType = R,
        ).let(::appTypeOf)
        val appParams = params.map { it.toBuilder(type = appTypeOf(it.type)).build() }
        val returnType = TypeMirrors.DFApp.parameterizedBy(F, R)
        val builder = FunSpec.builder("ap$i")
            .addTypeVariable(F)
            .addTypeVariable(Mu)
            .addTypeVariable(R)
            .addTypeVariables(PTypes)
            .addParameter("app", applicativeType)
            .addParameter("func", appFuncType)
            .addParameters(appParams)
            .returns(returnType)
        if (i == 1) { // Map kotlin function into java function
            builder.addStatement(
                "return app.ap(app.map({ f-> %T(f::invoke) }, func), %N)",
                TypeMirrors.JFunction,
                params.first().name
            )
        } else {
            val tailParams = params.drop(1)
            builder.addStatement(
                "return ap${i - 1}(app, ap1(app, app.map({ f -> { %1N: %2T -> { %3L -> f(p1, %4L)} }}, func), %1N), %4L)",
                params.first().name,
                params.first().type,
                tailParams
                    .joinToCode { param -> CodeBlock.of("%N: %T", param.name, param.type) },
                tailParams
                    .joinToCode { param -> CodeBlock.of("%N", param.name) }
            )
        }
        addFunction(builder.build())
    }
}
