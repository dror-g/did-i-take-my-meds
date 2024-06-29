package com.siravorona.utils

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

class WeakReferenceProperty<T>() : ReadWriteProperty<Any, T?> {
    constructor(value: T) : this() {
        reference = WeakReference(value)
    }

    private var reference: WeakReference<T?> = WeakReference(null)

    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        return reference.get()
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
        reference = WeakReference(value)
    }
}

fun <T> weak() = WeakReferenceProperty<T>()
fun <T> weak(value: T) = WeakReferenceProperty(value)

fun KMutableProperty0<Int>.incrementProgressCounter() {
    set(get() + 1)
}
fun KMutableProperty0<Int>.decrementProgressCounter() {
    val value = (get() - 1).coerceAtLeast(0)
    set(value)
}