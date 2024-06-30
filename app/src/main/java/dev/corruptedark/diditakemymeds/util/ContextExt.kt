package dev.corruptedark.diditakemymeds.util

import android.content.Context
import android.util.TypedValue

fun Context.getThemedColorByAttr(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.dpToPx(dp: Float) : Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ).toInt()
}