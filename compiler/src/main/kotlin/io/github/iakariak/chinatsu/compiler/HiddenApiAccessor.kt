package io.github.iakariak.chinatsu.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.writeTo
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

val HIDDEN_API = ClassName("", "__Chinatsu__")

private fun ClassName.varClassName() =
    canonicalName.replace(".", "_")

internal sealed interface RegistryItem {
    val className: ClassName
    val name: String
    val returnType: TypeName

    val handleVarName
        get() = className.canonicalName.replace(".", "_") +
                '$' + (if (this is Method) "m" else "f") +
                "_" + name

    fun TypeSpec.Builder.addAccessor(): TypeSpec.Builder

    data class Method(
        override val className: ClassName,
        override val name: String,
        override val returnType: TypeName,
        val params: List<TypeName>,
    ) : RegistryItem {
        val invokeFunctionName = "${handleVarName}_invoke"

        override fun TypeSpec.Builder.addAccessor(): TypeSpec.Builder = apply {
            val handleVarName = handleVarName
            addProperty(
                PropertySpec.builder(handleVarName, typeNameOf<MethodHandle>())
                    .jvmStatic()
                    .initializer(
                        "lookup.findVirtual(%N, %S, %T.methodType(%L))",
                        className.varClassName(),
                        name,
                        typeNameOf<MethodType>(),
                        (returnType + params).joinToCode { type ->
                            CodeBlock.of("%T::class.java", type.erased())
                        }
                    )
                    .build()
            )
            fun FunSpec.Builder.addInvokeParameters() = apply {
                params.forEachIndexed { index, param ->
                    addParameter("p$index", param)
                }
            }
            addFunction(
                FunSpec.builder(invokeFunctionName)
                    .returns(returnType)
                    .addInvokeParameters()
                    .addStatement(
                        "return %N.invokeExact(%L) as %T",
                        handleVarName,
                        params.mapIndexed { index, _ -> "p$index" }
                            .joinToCode(transform = CodeBlock::of),
                        returnType
                    )
                    .build()
            )
        }
    }

    data class Field(
        override val className: ClassName,
        override val name: String,
        override val returnType: TypeName,
    ) : RegistryItem {
        override fun TypeSpec.Builder.addAccessor() = this
    }

}

internal abstract class Registry {
    abstract class RClass(
        val className: ClassName,
        val genericCount: Int = 0
    ) {
        val items = mutableListOf<RegistryItem>()

        fun method(methodName: String, returnType: TypeName, vararg params: TypeName): CodeBlock {
            val item = RegistryItem.Method(className, methodName, returnType, params.toList())
            items.add(item)
            return CodeBlock.of("%T.%N", HIDDEN_API, item.invokeFunctionName)
        }

        fun field(fieldName: String, returnType: TypeName): CodeBlock {
            val item = RegistryItem.Field(className, fieldName, returnType)
            items.add(item)
            return CodeBlock.of("%T.%N", HIDDEN_API, item.handleVarName)
        }
    }

    fun scanInnerClasses(): List<RClass> = this::class.java.classes
        .filter { it.superclass == RClass::class.java }
        .mapNotNull { it.getField("INSTANCE").get(null) as? RClass }

}

private fun Registry.generateAccessor(): TypeSpec {
    val lookup = PropertySpec.builder("lookup", typeNameOf<MethodHandles.Lookup>(), KModifier.PRIVATE)
        .jvmStatic()
        .initializer("%T.lookup()", typeNameOf<MethodHandles>())
        .build()

    val classes = scanInnerClasses()
    val classProperties = classes.map { clazz ->
        PropertySpec.builder(
            clazz.className.varClassName(),
            Class::class.asClassName()
                .parameterizedBy(
                    clazz.className.takeIf { clazz.genericCount == 0 } ?: clazz.className.parameterizedBy(
                        List(clazz.genericCount) { STAR }
                    )
                ),
            KModifier.PRIVATE
        )
            .jvmStatic()
            .initializer("%T::class.java", clazz.className)
            .build()
    }

    fun TypeSpec.Builder.addAccessors() = apply {
        classes.asSequence().flatMap { it.items }.forEach { item ->
            with(item) {
                addAccessor()
            }
        }
    }

    val accessor = TypeSpec.objectBuilder(HIDDEN_API)
        .addProperty(lookup)
        .addProperties(classProperties)
        .addAccessors()
        .build()
    return accessor
}






internal object HiddenApiAccessor : Registry() {
    object Codec : RClass(TypeMirrors.Codec, 1) {
        val validate = method(
            "validate",
            TypeMirrors.Codec.parameterizedBy(STAR),
            JFunction::class.asTypeName().parameterizedBy(STAR, STAR)
        )
    }

    init {
        Codec.validate
    }
}

private var generated = false

context(env: ProcessEnv)
fun TypeMirrors.generateHiddenApiAccessor() {
    if (generated) return
    val accessor = HiddenApiAccessor.generateAccessor()
    FileSpec.builder(HIDDEN_API)
        .addType(accessor)
        .build()
        .writeTo(env.codeGenerator, true)
    generated = true
}