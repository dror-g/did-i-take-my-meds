package com.siravorona.utils.lists

import androidx.annotation.MainThread
import androidx.databinding.ListChangeRegistry
import androidx.databinding.ObservableList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback

/**
 * An [ObservableList] that uses [DiffUtil] to calculate and dispatch its change updates.
 *
 * @param callback    The callback that controls the behavior of the DiffObservableList.
 * @param detectMoves True if DiffUtil should try to detect moved items, false otherwise.
 */
class DiffObservableList<T>(
    private val callback: Callback<T>,
    private val detectMoves: Boolean = true
): AbstractMutableList<T>(), ObservableList<T> {
    private val LIST_LOCK = Any()
    private val list = mutableListOf<T>()
    private val listeners = ListChangeRegistry()
    private val listCallback = ObservableListUpdateCallback()

    /**
     * Calculates the list of update operations that can convert this list into the given one.
     *
     * @param newItems The items that this list will be set to.
     * @return A DiffResult that contains the information about the edit sequence to covert this
     * list into the given one.
     */
    fun calculateDiff(newItems: List<T>): DiffUtil.DiffResult {
        lateinit var frozenList: List<T>
        synchronized(LIST_LOCK) {
            frozenList = list.toList()
        }
        return doCalculateDiff(frozenList, newItems)
    }

    private fun doCalculateDiff(oldItems: List<T>, newItems: List<T>): DiffUtil.DiffResult {
        return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return oldItems.size
            }

            override fun getNewListSize(): Int {
                return newItems.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return callback.areItemsTheSame(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return callback.areContentsTheSame(oldItem, newItem)
            }
        }, detectMoves)
    }

    /**
     * Updates the contents of this list to the given one using the DiffResults to dispatch change
     * notifications.
     *
     * @param newItems   The items to set this list to.
     * @param diffResult The diff results to dispatch change notifications.
     */
    @MainThread
    fun update(newItems: List<T>, diffResult: DiffUtil.DiffResult) {
        synchronized(LIST_LOCK) {
            list.clear()
            list.addAll(newItems)
        }
        diffResult.dispatchUpdatesTo(listCallback)
    }

    /**
     * Sets this list to the given items. This is a convenience method for calling [ ][.calculateDiff] followed by [.update].
     *
     *
     * **Warning!** If the lists are large this operation may be too slow for the main thread. In
     * that case, you should call [.calculateDiff] on a background thread and then
     * [.update] on the main thread.
     *
     * @param newItems The items to set this list to.
     */
    @MainThread
    fun update(newItems: List<T>) {
        val diffResult = calculateDiff(newItems)
        update(newItems, diffResult)
    }

    override fun addOnListChangedCallback(listener: ObservableList.OnListChangedCallback<out ObservableList<T>>) {
        listeners.add(listener)
    }

    override fun removeOnListChangedCallback(listener: ObservableList.OnListChangedCallback<out ObservableList<T>>) {
        listeners.remove(listener)
    }

    override val size: Int
        get() = list.size

    override fun get(index: Int): T {
        return list[index]
    }

    override fun set(index: Int, element: T): T {
        val result = list.set(index, element)
        listeners.notifyChanged(this, index, 1)
        return result
    }

    override fun add(index: Int, element: T) {
        list.add(index, element)
        listeners.notifyInserted(this, index, 1)
    }

    override fun removeAt(index: Int): T {
        val result = list.removeAt(index)
        listeners.notifyRemoved(this, index, 1)
        return result
    }

    fun getItemIndex(item: T, isMatchingItem: (T, T) -> Boolean): Int {
        val itemInList = list.firstOrNull { isMatchingItem(it, item) }
        return this.indexOf(itemInList)
    }

    /**
     * A Callback class used by DiffUtil while calculating the diff between two lists.
     */
    interface Callback<T> {

        /**
         * Called by the DiffUtil to decide whether two object represent the same Item.
         *
         *
         * For example, if your items have unique ids, this method should check their id equality.
         *
         * @param oldItem The old item.
         * @param newItem The new item.
         * @return True if the two items represent the same object or false if they are different.
         */
        fun areItemsTheSame(oldItem: T, newItem: T): Boolean

        /**
         * Called by the DiffUtil when it wants to check whether two items have the same data.
         * DiffUtil uses this information to detect if the contents of an item has changed.
         *
         *
         * DiffUtil uses this method to check equality instead of [Object.equals] so
         * that you can change its behavior depending on your UI.
         *
         *
         * This method is called only if [.areItemsTheSame] returns `true` for
         * these items.
         *
         * @param oldItem The old item.
         * @param newItem The new item which replaces the old item.
         * @return True if the contents of the items are the same or false if they are different.
         */
        fun areContentsTheSame(oldItem: T, newItem: T): Boolean
    }

    internal inner class ObservableListUpdateCallback : ListUpdateCallback {

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            listeners.notifyChanged(this@DiffObservableList, position, count)
        }

        override fun onInserted(position: Int, count: Int) {
            modCount += 1
            listeners.notifyInserted(this@DiffObservableList, position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            modCount += 1
            listeners.notifyRemoved(this@DiffObservableList, position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            listeners.notifyMoved(this@DiffObservableList, fromPosition, toPosition, 1)
        }
    }
}