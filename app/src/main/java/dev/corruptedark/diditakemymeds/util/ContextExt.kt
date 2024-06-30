package dev.corruptedark.diditakemymeds.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat

fun Context.getThemedColorByAttr(attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

fun Context.getThemeDrawableByAttr(attr: Int): Drawable? {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return AppCompatResources.getDrawable(this, typedValue.resourceId)
}

fun Context.dpToPx(dp: Float) : Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ).toInt()
}