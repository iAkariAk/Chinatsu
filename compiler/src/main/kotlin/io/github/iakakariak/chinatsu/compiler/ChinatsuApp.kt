package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakakariak.chinatsu.annotation.ChinatsuApp
import io.github.iakakariak.chinatsu.annotation.SideType
import io.github.iakakariak.chinatsu.compiler.module.registerConfig
import io.github.iakakariak.chinatsu.compiler.module.registerInit
import io.github.iakakariak.chinatsu.compiler.module.registerSubscribeEvent


context(env: ProcessEnv)
private fun ChinatsuAppSetupRegisterScope.registerSetup() {
    registerConfig()
    registerInit()
    registerSubscribeEvent()
}

interface ChinatsuAppSetupRegisterScope : NotifyScope {
    fun addClient(name: String, block: CodeBlock)
    fun addServer(name: String, block: CodeBlock)
    fun addCommon(name: String, block: CodeBlock)

    fun add(sideType: SideType, name: String, block: CodeBlock) = when (sideType) {
        SideType.Server -> addServer(name, block)
        SideType.Client -> addClient(name, block)
        SideType.Common -> addCommon(name, block)
    }
}

private class ChinatsuAppSetupRegisterScopeImpl : ChinatsuAppSetupRegisterScope, NotifyScope by NotifyScope() {
    private typealias RegisterEnter = Pair<String, CodeBlock>

    private val clientBlocks = mutableListOf<RegisterEnter>()
    private val serverBlocks = mutableListOf<RegisterEnter>()
    private val commonBlocks = mutableListOf<RegisterEnter>()

    override fun addClient(name: String, block: CodeBlock) {
        clientBlocks.add(name to block)
    }

    override fun addServer(name: String, block: CodeBlock) {
        serverBlocks.add(name to block)
    }

    override fun addCommon(name: String, block: CodeBlock) {
        commonBlocks.add(name to block)
    }

    fun getSetupBlock(sideType: SideType) = when (sideType) {
        SideType.Server -> serverBlocks
        SideType.Client -> clientBlocks
        SideType.Common -> commonBlocks
    }.map { (name, block) ->
        buildCodeBlock {
            addStatement("// Module: $name")
            add(block)
        }
    }.joinToCode("\n")
}

context(env: ProcessEnv)
fun TypeMirrors.generateChinatsuApp() = env.createFile {
    val register = ChinatsuAppSetupRegisterScopeImpl()
    register.apply { registerSetup() }
    fun setupFunc(sideType: SideType) = FunSpec.builder("c_setup$sideType")
        .addModifiers(KModifier.PRIVATE)
        .addCode(register.getSetupBlock(sideType))
        .build()

    val clientSetup = setupFunc(SideType.Client)
    val serverSetup = setupFunc(SideType.Server)
    val commonSetup = setupFunc(SideType.Common)

    env.resolver.getSymbolsWithAnnotation(annotation<ChinatsuApp>())
        .filterIsInstance<KSClassDeclaration>()
        .filter {
            (Modifier.OPEN in it.modifiers)
                .onFalse { env.logger.error("The entry class must be `open`", it) }
        }
        .filter {
            ModInitializer.asStarProjectedType().isAssignableFrom(it.asStarProjectedType())
                .onFalse {
                    env.logger.error("Your app must extend ModInitializer")
                }
        }
        .forEach { declaration ->
            declaration.containingFile?.let { notifyChange(it) }
            val origName = declaration.toClassName()
            val onInitialize = FunSpec.builder("onInitialize")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addStatement("super.onInitialize()")
                .addStatement(
                    "val env = %T.getInstance().environmentType",
                    FabricLoader.toClassName()
                )
                .beginControlFlow("when (env)")
                .beginControlFlow("%T.SERVER ->", EnvType.toClassName())
                .addStatement("c_setupServer()")
                .endControlFlow()
                .beginControlFlow("%T.CLIENT ->", EnvType.toClassName())
                .addStatement("c_setupClient()")
                .endControlFlow()
                .endControlFlow()
                .addStatement("c_setupCommon()")
                .build()
            val genName = ClassName(
                declaration.packageName.asString(),
                declaration.simpleName.asString() + "_ChinatsuApp"
            )

            val app = TypeSpec.classBuilder(genName)
                .attachNotifies()
                .addKdoc("Note: Use the class to replace origin in your fabric config main\n")
                .addKdoc("The generated class delegates original class to initialize those annotated @Init\n")
                .addKdoc("Besides it's also the injury place of other Chinatsu function\n")
                .superclass(origName)
                .addFunction(serverSetup)
                .addFunction(clientSetup)
                .addFunction(commonSetup)
                .addFunction(onInitialize)
                .build()
            FileSpec.builder(genName)
                .addAnnotation(
                    AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "ClassName")
                        .addMember("%S", "FunctionName")
                        .addMember("%S", "RemoveRedundantQualifierName")
                        .addMember("%S", "RedundantVisibilityModifier")
                        .addMember("%S", "SpellCheckingInspection")
                        .build()
                )
                .addType(app)
                .build()
                .writeTo(env.codeGenerator, false)
        }
}
