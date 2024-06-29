package dev.corruptedark.diditakemymeds.util

import android.util.Log
import android.widget.Toolbar
import androidx.databinding.BindingAdapter
import com.google.android.material.appbar.MaterialToolbar

@BindingAdapter("zeroContentInsetStart")
fun setNoContentInsetStart(view: MaterialToolbar, noContentInsetStart: Boolean) {
    if (noContentInsetStart) {
        Log.d("Toolbar", "zeroContentInsetStart")
        view.setContentInsetsRelative(0, view.contentInsetEnd)
    }
}

@BindingAdapter("zeroNavigationContentInsetStart")
fun setNoNavigationContentInsetStart(view: MaterialToolbar, zeroNavigationContentInsetStart: Boolean) {
    if (zeroNavigationContentInsetStart) {
        Log.d("Toolbar", "contentInsetStartWithNavigation")
        view.contentInsetStartWithNavigation = 0
    }
}