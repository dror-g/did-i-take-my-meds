package com.siravorona.utils.lists

import androidx.databinding.ListChangeRegistry
import androidx.databinding.ObservableList
import androidx.databinding.ObservableList.OnListChangedCallback


fun <T> observableListOf(vararg elements: T): ObservableList<T> {
    return improvedObservableListOf(*elements)
}

fun <T> observableListOf(
        collection: Collection<T> = emptyList(),
        callback: OnListChangedCallback<ObservableList<T>>? = null,
): ObservableList<T> {
    return  improvedObservableListOf(collection, callback)
}

fun <T> observableListOf(
        collection: Collection<T> = emptyList(),
        onChanged: (ObservableList<T>) -> Unit,
): ObservableList<T> {
    return  improvedObservableListOf(collection, onChanged)
}

fun <T> improvedObservableListOf(vararg elements: T): ImprovedObservableArrayList<T> {
    val list = ImprovedObservableArrayList<T>()
    list.addAll(elements)
    return list
}

fun <T> improvedObservableListOf(
        collection: Collection<T> = emptyList(),
        callback: OnListChangedCallback<ObservableList<T>>? = null,
): ImprovedObservableArrayList<T> {
    val list = ImprovedObservableArrayList<T>()
    if (callback != null) {
        list.addOnListChangedCallback(callback)
    }
    list.addAll(collection)
    return list
}

fun <T> improvedObservableListOf(
        collection: Collection<T> = emptyList(),
        onChanged: (ObservableList<T>) -> Unit,
): ImprovedObservableArrayList<T> {
    return improvedObservableListOf(collection,
            callback = object : OnListChangedCallback<ObservableList<T>>() {
                override fun onChanged(sender: ObservableList<T>) {
                    onChanged(sender)
                }

                override fun onItemRangeChanged(sender: ObservableList<T>, i: Int, i1: Int) {
                    onChanged(sender)
                }

                override fun onItemRangeInserted(sender: ObservableList<T>, start: Int, count: Int) {
                    onChanged(sender)
                }

                override fun onItemRangeMoved(
                        sender: ObservableList<T>,
                        fromPosition: Int,
                        toPosition: Int,
                        itemCount: Int,
                ) {
                    onChanged(sender)
                }

                override fun onItemRangeRemoved(
                        sender: ObservableList<T>,
                        positionStart: Int,
                        itemCount: Int,
                ) {
                    onChanged(sender)
                }
            })
}

/*
* ObservableArrayList with removeAll, sortWithAndNotify that notifies observers and DiffUtil support
* */
class ImprovedObservableArrayList<T>() : ArrayList<T>(), ObservableList<T> {

    private val LIST_LOCK = Any()

    @Transient
    private val listeners = ListChangeRegistry()
    override fun addOnListChangedCallback(callback: OnListChangedCallback<out ObservableList<T>>) {
        listeners.add(callback)
    }

    override fun removeOnListChangedCallback(callback: OnListChangedCallback<out ObservableList<T>>) {
        listeners.remove(callback)
    }


    // region list operations
    override fun add(element: T): Boolean {
        super.add(element)
        notifyAdd(size - 1, 1)
        return true
    }

    override fun add(index: Int, element: T) {
        super.add(index, element)
        notifyAdd(index, 1)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val oldSize = size
        val added = super.addAll(elements)
        if (added) {
            notifyAdd(oldSize, size - oldSize)
        }
        return added
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val added = super.addAll(index, elements)
        if (added) {
            notifyAdd(index, elements.size)
        }
        return added
    }

    override fun clear() {
        val oldSize = size
        super.clear()
        if (oldSize != 0) {
            notifyRemove(0, oldSize)
        }
    }

    override fun remove(element: T): Boolean {
        val index = indexOf(element)
        val result = super.remove(element)
        notifyRemove(index, 1)
        return result
    }

    override fun removeAt(index: Int): T {
        val result = super.removeAt(index)
        notifyRemove(index, 1)
        return result
    }

    override fun removeAll(elements: Collection<T>): Boolean {

        val rangesToRemove = ListUtil.indexRangesOf(this, elements)
        if (rangesToRemove.isEmpty()) return false
        var removedCount = 0
        for (range in rangesToRemove) {
            removeRange(range.first - removedCount, range.last - removedCount + 1)
            removedCount += range.last - range.first + 1
        }
        return removedCount != 0

    }

    override fun set(index: Int, element: T): T {
        val result = super.set(index, element)
        listeners.notifyChanged(this, index, 1)
        return result
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        super.removeRange(fromIndex, toIndex)
        notifyRemove(fromIndex, toIndex - fromIndex)
    }

    fun sortWithAndNotify(comparator: Comparator<T>) {
        sortWith(comparator)
        notifyChanged()
    }

    private fun notifyAdd(start: Int, count: Int) {
        listeners.notifyInserted(this, start, count)
    }

    private fun notifyRemove(start: Int, count: Int) {
        listeners.notifyRemoved(this, start, count)
    }

    private fun notifyChanged() {
        listeners.notifyChanged(this)
    }
    // endregion

}
