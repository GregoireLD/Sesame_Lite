package com.duval.sesamelite

import android.app.Application
import com.duval.sesamelite.data.db.AppDatabase
import com.duval.sesamelite.geo.GeofenceManager
import com.duval.sesamelite.migration.MigrationManager
import com.duval.sesamelite.notification.NotificationHelper
import com.duval.sesamelite.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SesameApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        NotificationHelper.createChannel(this)

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(this@SesameApplication).accessCodeDao()
            MigrationManager.migrateIfNeeded(dao)
            GeofenceManager.registerAll(this@SesameApplication, dao.getAll(), currentLocation = null)
        }
    }
}
