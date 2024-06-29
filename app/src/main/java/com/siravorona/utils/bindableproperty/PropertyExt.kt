package com.siravorona.utils.bindableproperty

import kotlin.reflect.KProperty

inline val KProperty<*>.bindingId: Int
    @JvmSynthetic get() = BindableManager.getBindingIdByProperty(this)

fun <T> BindableObservable.bindableProperty(value: T) = BindableProperty(value)
fun <T> BindableObservable.bindableProperty(value: T, afterChange: (T, T) -> Unit) = BindableProperty(value, afterChange)

fun <T> BindableObservable.weakBindableProperty(value: T) = WeakReferenceBindableProperty(value)