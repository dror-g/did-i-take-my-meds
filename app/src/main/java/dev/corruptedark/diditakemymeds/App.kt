package dev.corruptedark.diditakemymeds

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.bindableproperty.BindableManager
import dev.corruptedark.diditakemymeds.util.NotificationsUtil

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BindableManager.loadBR(BR::class.java)
        ActivityResultManager.init(this)
        NotificationsUtil.createNotificationChannel(this)
    }
}