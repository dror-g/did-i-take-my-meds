package com.siravorona.utils.lists

import androidx.recyclerview.widget.DiffUtil

object ListUtil {
    fun <T> indexRangesOf(list: List<T>, elements: Collection<T>): List<IntRange> {
        val s = elements.toSet()
        return indexRangesOf(list, s) { listElement, otherCollection ->
            otherCollection.contains(listElement)
        }
    }

     fun <T> indexRangesOf(list: List<T>, otherCollection: Collection<T>, predicate: (T, Collection<T>) -> Boolean): List<IntRange> {
         val indexes = list.mapIndexedNotNull { index, listElement ->
             if (predicate(listElement, otherCollection)) index else null
         }.sorted()
         if (indexes.isEmpty()) return emptyList()
         if (indexes.size == 1) return listOf(indexes[0].rangeTo(indexes[0]))
         val ranges = mutableListOf<IntRange>()
         var start = indexes[0]
         var previous = indexes[0]
         for (i in 1 until indexes.size) {
             val current = indexes[i]
             if (current > previous + 1) {
                 ranges.add(start.rangeTo(previous))
                 start = current
             }
             previous = current
         }
         ranges.add(start.rangeTo(previous))
         return ranges.toList()
     }

    fun <T>calculateDiff(oldItems: List<T>, newItems: List<T>, callback: DiffUtil.ItemCallback<T>, detectMoves: Boolean): DiffUtil.DiffResult {
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
                if (newItem == null || oldItem == null) return (newItem == oldItem)
                return callback.areItemsTheSame(oldItem, newItem)
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                if (newItem == null || oldItem == null) return (newItem == oldItem)
                return callback.areContentsTheSame(oldItem, newItem)
            }
        }, detectMoves)
    }

    fun <R: Any, T: R> insertSeparators(source: List<T>, generator: (itemBefore: T?, itemAfter: T?) -> R?) : List<R>{
        val mapped = mutableListOf<R>()
        // Intentionally including lastIndex + 1 for the footer.
        for (i in 0..source.size) {
            val itemBefore = source.getOrNull(i - 1)
            val item = source.getOrNull(i)
            val separator = generator(itemBefore, item)
            if (separator != null) {
                mapped.add(separator)
            }
            if (item != null) {
                mapped.add(item)
            }
        }
        return mapped.toList()
    }
}

fun <R: Any, T: R> List<T>.insertSeparators(generator: (itemBefore: T?, itemAfter: T?) -> R?) : List<R> {
    return ListUtil.insertSeparators(this, generator)
}
fun <E> MutableCollection<E>.removeIff(filter: (E) -> Boolean): Boolean {
    var removed = false
    val iterator: MutableIterator<E> = this.iterator()
    while (iterator.hasNext()) {
        val value = iterator.next()
        if (filter.invoke(value)) {
            iterator.remove()
            removed = true
        }
    }
    return removed
}