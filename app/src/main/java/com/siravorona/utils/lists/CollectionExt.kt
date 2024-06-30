package com.siravorona.utils.lists

fun <T> Collection<T>.anyOf(other: Collection<T>): Boolean {
    return any(other::contains)
}

fun <T> Collection<T>.anyOf(vararg other: T): Boolean {
    return anyOf(other.toSet())
}

fun <T: Any, R: Any> Map<T,Collection<R>>.totalSize(): Int {
    return values.fold(0) { acc, item -> acc + item.size }
}

fun <T: Any, R: Any> Map<T,Collection<R>>.averageSize(): Double {
    return if (isEmpty()) 0.0 else totalSize() / size.toDouble()
}