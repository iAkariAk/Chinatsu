@file:OptIn(KspExperimental::class)

package io.github.iakakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile

internal inline fun <reified T : Annotation> annotation() =
    T::class.qualifiedName!!

inline fun Boolean.onTrue(block: () -> Unit): Boolean = also { if (it) block() }
inline fun Boolean.onFalse(block: () -> Unit): Boolean = also { if (!it) block() }


val KSFile.jvmName: String get() =
    this.getAnnotationsByType(JvmName::class).firstOrNull()?.name ?: "${fileName.removeSuffix(".kt")}Kt"


val KSDeclaration.absolutePath: String get() =
    packageName.asString() + "." + simpleName.asString()