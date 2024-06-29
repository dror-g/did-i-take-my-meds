package com.siravorona.utils.bindableproperty

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

@Suppress("ktPropBy")
class WeakReferenceBindableProperty<T>(
    value: T
) {
    private var refValue = WeakReference(value)

    operator fun getValue(bindingObservable: BindableObservable, property: KProperty<*>): T? = refValue.get()

    operator fun setValue(bindingObservable: BindableObservable, property: KProperty<*>, value: T)  {
        if (refValue.get() != value) {
            refValue = WeakReference(value)
            bindingObservable.notifyPropertyChanged(property.bindingId)
        }
    }
}