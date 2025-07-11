@file:OptIn(KspExperimental::class)

package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.iakakariak.chinatsu.annotation.ChinatsuApp
import io.github.iakakariak.chinatsu.annotation.Init
import io.github.iakakariak.chinatsu.annotation.SideType

class ChinatsuProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = ChinatsuProcessor(environment)
}

class ChinatsuProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator get() = environment.codeGenerator
    private val logger get() = environment.logger
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val env = ProcessEnv(environment, resolver)
        val mirrors = TypeMirrors(resolver)
        with(mirrors) {
            context(env) {
                generateInit()
            }
        }

        return emptyList()
    }

    context(env: ProcessEnv)
    private fun TypeMirrors.generateInit() = env.createFile {
        val resolver = env.resolver
        val inits = generateSideInits(resolver)
        fun specifyInits(sideType: SideType) = inits
            .filter { (side, _) -> side == sideType }
            .map { (_, block) -> block }
            .joinToCode("\n", suffix = "\n")

        val clientInits = specifyInits(SideType.Client)
        val serverInits = specifyInits(SideType.Server)
        val bothInits = specifyInits(SideType.Both)

        resolver.getSymbolsWithAnnotation(annotation<ChinatsuApp>())
            .mapNotNull { it as? KSClassDeclaration }
            .filter {
                (Modifier.OPEN in it.modifiers)
                    .onFalse { logger.error("The entry class must be `open`", it) }
            }
            .filter {
                ModInitializer.asStarProjectedType().isAssignableFrom(it.asStarProjectedType())
                    .onFalse {
                        logger.error("Your app must extend ModInitializer")
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
                    .addCode(serverInits)
                    .endControlFlow()
                    .beginControlFlow("%T.CLIENT ->", EnvType.toClassName())
                    .addCode(clientInits)
                    .endControlFlow()
                    .endControlFlow()
                    .addCode(bothInits)
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
                    .addFunction(onInitialize)
                    .build()
                FileSpec.builder(genName)
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "ClassName")
                            .addMember("%S", "RemoveRedundantQualifierName")
                            .addMember("%S", "RedundantVisibilityModifier")
                            .addMember("%S", "SpellCheckingInspection")
                            .build()
                    )
                    .addType(app)
                    .build()
                    .writeTo(codeGenerator, false)
            }
    }


    private fun FileScope.generateSideInits(resolver: Resolver): List<Pair<SideType, CodeBlock>> {
        val inits = resolver.getSymbolsWithAnnotation(annotation<Init>()).map {
            it.getAnnotationsByType(Init::class).first().side to it
        }.mapNotNull { (side, declaration) ->
            declaration.containingFile?.let { notifyChange(it) }
            // import is tricky when conflicting, henceforward we use entire name
            side to when (declaration) {
                is KSFile -> CodeBlock.of(
                    "Class.forName(%S)",
                    ClassName(declaration.packageName.asString(), declaration.jvmName)
                )

                is KSClassDeclaration -> CodeBlock.of(
                    "Class.forName(%1L::class.java.name, true, %1L::class.java.classLoader)",
                    declaration.toClassName()
                )

                is KSPropertyDeclaration -> CodeBlock.of(
                    "%L", declaration.absolutePath
                )

                is KSFunctionDeclaration -> {
                    if (declaration.parameters.isNotEmpty()) {
                        logger.error("Only an empty args function can it be initialized")
                        return@mapNotNull null
                    }

                    CodeBlock.of(
                        "%L()", declaration.absolutePath
                    )
                }

                else -> {
                    logger.error("Unsupported target")
                    return@mapNotNull null
                }
            }
        }.toList()
        return inits
    }
}
