package com.siravorona.utils.bindableproperty

import java.util.*
import kotlin.reflect.KProperty

object BindableManager {

    private val  bindingFieldsMap = mutableMapOf<String, Int>()

    fun loadBR(brClass: Class<*>) {
        synchronized(this) {
            brClass.fields.forEach { field ->
                    try {
                        val propName = field.name
                        val bindableId = brClass.getDeclaredField(propName).getInt(brClass)
                        bindingFieldsMap[propName] = bindableId
                    } catch (ignore: Throwable) {
                    }
                }

        }
    }

    private const val JAVA_BEANS_BOOLEAN = "is"

    fun getBindingIdByProperty(property: KProperty<*>): Int {
        val propertyName = property.name.replaceFirstChar { it.lowercase(Locale.ENGLISH) }
        val bindingPropertyName = propertyName
            .takeIf { it.startsWith(JAVA_BEANS_BOOLEAN) }
            ?.replaceFirst(JAVA_BEANS_BOOLEAN, String())
            ?.replaceFirstChar { it.lowercase(Locale.ENGLISH) } ?: propertyName
        return bindingFieldsMap[bindingPropertyName] ?: 0
    }
}
