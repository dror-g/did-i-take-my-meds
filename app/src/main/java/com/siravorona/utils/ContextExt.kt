package com.siravorona.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import java.util.Locale

@Suppress("DEPRECATION")
fun Context.getCurrentLocale() : Locale {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
        resources.configuration.locales.get(0)
    } else{
        resources.configuration.locale
    }
}

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

fun Context.dp2Px(dp: Float) : Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}