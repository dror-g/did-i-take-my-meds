package dev.corruptedark.diditakemymeds.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

fun Context.broadcastIntentFromIntent(requestCode: Int, innerIntent: Intent): PendingIntent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.getBroadcast(this, requestCode, innerIntent, PendingIntent.FLAG_IMMUTABLE)
    } else {
        PendingIntent.getBroadcast(this, requestCode, innerIntent, 0)
    }
}

fun Context.activityIntentFromIntent(requestCode: Int, actionIntent: Intent): PendingIntent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.getActivity(this, requestCode, actionIntent, PendingIntent.FLAG_IMMUTABLE)
    } else {
        PendingIntent.getActivity(this, requestCode, actionIntent, 0)
    }
}