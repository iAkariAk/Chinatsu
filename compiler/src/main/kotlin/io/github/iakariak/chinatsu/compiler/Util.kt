@file:OptIn(KspExperimental::class)

package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toClassName

internal inline fun <reified T : Annotation> annotation() =
    T::class.qualifiedName!!

inline fun Boolean.onTrue(block: () -> Unit): Boolean = also { if (it) block() }
inline fun Boolean.onFalse(block: () -> Unit): Boolean = also { if (!it) block() }
inline fun <R> R.onNull(block: () -> Unit): R = also { it ?: block() }


inline fun <reified T> qualificationOf() = T::class.qualifiedName!!
inline fun <reified T> typeNameStringOf() = T::class.qualifiedName!!


val KSFile.fileNameWithoutExtension: String
    get() = fileName.removeSuffix(".kt")

val KSFile.jvmName: String
    get() = this.getAnnotationsByType(JvmName::class).firstOrNull()?.name ?: "${fileNameWithoutExtension}Kt"

val KSDeclaration.absolutePath: String
    get() = packageName.asString() + "." + simpleName.asString()


fun KSPropertyDeclaration.toMemberName(): MemberName {
    val parent = parent as? KSClassDeclaration
    val isTopLevel = parent == null
    return if (isTopLevel) MemberName(packageName.asString(), simpleName.asString())
    else MemberName(parent.toClassName(), simpleName.asString())
}

fun KSFunctionDeclaration.toMemberName(): MemberName {
    val parent = parent as? KSClassDeclaration
    val isTopLevel = parent == null
    return if (isTopLevel) MemberName(packageName.asString(), simpleName.asString())
    else MemberName(parent.toClassName(), simpleName.asString())
}