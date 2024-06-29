package com.siravorona.utils.listadapters

import androidx.databinding.ObservableList
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter class for easy databinding. Based on LastAdapter.
 *
 * How to use
 *
 * Say, you have:
 *  - data class ItemSection(val name: String) and databinding layout item_section.xml,
 *    with variable "item" of ItemSection type;
 *  - data class ItemEntry(val name: String, val value: Int) and databinding layout item_entry.xml,
 *    with variable "item" of ItemEntry type.
 *
 * !!!!! All item types used in same adapter must have the same variable name.
 *
 * Then:
 *
 * BindableAdapter(listOfItems, BR.item)
 *     .map<ItemSection, ItemSectionBinding>(R.layout.item_section)
 *     .map<ItemEntry, ItemEntryBinding>(R.layout.item_entry) {
 *         onClick {
 *              handleItemEntryCLick()
 *         }
 *     }
 *     .into(recyclerView)
 *
 * If you want the recycler view to be automatically updated when content of the list of items changes,
 * use ObservableList.
 * */
class BindableAdapter(
    private val itemsList: List<Any>,
    variable: Int? = null,
) : BaseBindableAdapter(variable) {
    constructor(list: List<Any>) : this(list, null)

    private var observableCallback = ObservableListCallback(this)


    override fun getItemCount(): Int {
        return itemsList.size
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        if (recyclerView == null && itemsList is ObservableList) {
            itemsList.addOnListChangedCallback(observableCallback)
        }
        super.onAttachedToRecyclerView(rv)
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        if (recyclerView != null && itemsList is ObservableList) {
            itemsList.removeOnListChangedCallback(observableCallback)
        }
        super.onDetachedFromRecyclerView(rv)
    }

    override fun getItemAtPosition(position: Int): Any {
        return itemsList[position]
    }
}
