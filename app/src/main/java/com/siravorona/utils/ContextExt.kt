package com.siravorona.utils

import android.content.Context
import android.os.Build
import java.util.Locale

@Suppress("DEPRECATION")
fun Context.getCurrentLocale() : Locale {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
        resources.configuration.locales.get(0)
    } else{
        resources.configuration.locale
    }
}