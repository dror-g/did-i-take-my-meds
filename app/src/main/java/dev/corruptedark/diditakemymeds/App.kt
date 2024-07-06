package dev.corruptedark.diditakemymeds

import android.app.Application
import com.chibatching.kotpref.Kotpref
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.bindableproperty.BindableManager
import dev.corruptedark.diditakemymeds.util.notifications.NotificationsUtil

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        BindableManager.loadBR(BR::class.java)
        ActivityResultManager.init(this)
        NotificationsUtil.createNotificationChannels(this)
    }
}