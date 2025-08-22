@file:OptIn(KspExperimental::class, ExperimentalContracts::class)

package io.github.iakariak.chinatsu.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias JFunction<T, R> = java.util.function.Function<T, R>

internal inline fun <reified T : Annotation> annotation() =
    T::class.qualifiedName!!

internal inline fun Boolean.onTrue(block: () -> Unit): Boolean = also { if (it) block() }
internal inline fun Boolean.onFalse(block: () -> Unit): Boolean = also { if (!it) block() }
internal inline fun <R> R.onNull(block: () -> Unit): R = also { it ?: block() }
internal inline fun <R> R.transformIf(predicate: (R) -> Boolean, transform: (R) -> R): R {
    contract {
        callsInPlace(predicate, InvocationKind.EXACTLY_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }
    return if (predicate(this)) transform(this) else this
}

@Suppress("NOTHING_TO_INLINE")
internal inline operator fun <T> T.plus(others: List<T>) = buildList<T> {
    add(this@plus)
    addAll(others)
}


internal inline fun <reified T> qualificationOf() = T::class.qualifiedName!!
internal inline fun <reified T> typeNameStringOf() = T::class.qualifiedName!!


internal val KSFile.fileNameWithoutExtension: String
    get() = fileName.removeSuffix(".kt")

internal val KSFile.jvmName: String
    get() = this.getAnnotationsByType(JvmName::class).firstOrNull()?.name ?: "${fileNameWithoutExtension}Kt"

internal val KSDeclaration.absolutePath: String
    get() = packageName.asString() + "." + simpleName.asString()


internal fun KSPropertyDeclaration.toMemberName(): MemberName {
    val parent = parent as? KSClassDeclaration
    val isTopLevel = parent == null
    return if (isTopLevel) MemberName(packageName.asString(), simpleName.asString())
    else MemberName(parent.toClassName(), simpleName.asString())
}

internal fun KSFunctionDeclaration.toMemberName(): MemberName {
    val parent = parent as? KSClassDeclaration
    val isTopLevel = parent == null
    return if (isTopLevel) MemberName(packageName.asString(), simpleName.asString())
    else MemberName(parent.toClassName(), simpleName.asString())
}

internal fun TypeName.erased() = transformIf({ it is ParameterizedTypeName }) { (it as ParameterizedTypeName).rawType }