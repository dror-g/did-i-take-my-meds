package com.siravorona.utils.listadapters

import androidx.recyclerview.widget.DiffUtil

@Suppress("UNCHECKED_CAST")
class DiffCallbackBuilder: DiffUtil.ItemCallback<Any>() {
    private val typesMap = mutableMapOf< Class<*>, DiffUtil.ItemCallback<out Any>> ()

    fun <R: Any> registerItemCallback(clazz: Class<R>, callback: DiffUtil.ItemCallback<R>) = apply {
        typesMap[clazz] = callback
    }

    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem.javaClass != newItem.javaClass) return false
        val callback = typesMap[oldItem.javaClass]  as? DiffUtil.ItemCallback<Any> ?: return false
        return callback.areItemsTheSame(oldItem, newItem)
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem.javaClass != newItem.javaClass) return false
        val callback = typesMap[oldItem.javaClass]  as? DiffUtil.ItemCallback<Any> ?: return false
        return callback.areContentsTheSame(oldItem, newItem)
    }


}