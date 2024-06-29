package dev.corruptedark.diditakemymeds.util

import androidx.databinding.BindingAdapter
import com.google.android.material.appbar.MaterialToolbar

@BindingAdapter("zeroContentInsetStart")
fun setNoContentInsetStart(view: MaterialToolbar, noContentInsetStart: Boolean) {
    if (noContentInsetStart) {
        view.setContentInsetsRelative(0, view.contentInsetEnd)
    }
}
@BindingAdapter("zeroNavigationContentInsetStart")
fun setNoNavigationContentInsetStart(view: MaterialToolbar, zeroNavigationContentInsetStart: Boolean) {
    if (zeroNavigationContentInsetStart) {
        view.contentInsetStartWithNavigation = 0
    }
}