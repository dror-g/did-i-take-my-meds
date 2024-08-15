package dev.corruptedark.diditakemymeds.util

import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.addDefaultDivider() {
    val orientation = (this.layoutManager as? LinearLayoutManager)?.orientation ?: LinearLayoutManager.VERTICAL
    this.addItemDecoration(DividerItemDecoration(this.context, orientation))
}