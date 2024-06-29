package com.siravorona.utils.bindableproperty

import kotlin.reflect.KProperty

@Suppress("ktPropBy")
class BindableProperty<T>(
    private var value: T,
    private val afterChange: ((oldValue: T, value: T ) -> Unit)? = null
) {
    operator fun getValue(bindingObservable: BindableObservable, property: KProperty<*>): T = value

    operator fun setValue(bindingObservable: BindableObservable, property: KProperty<*>, value: T) {
        if (this.value == value) return
        val oldValue = this.value
        this.value = value
        afterChange?.invoke(oldValue, value)
        bindingObservable.notifyPropertyChanged(property.bindingId)

    }
}