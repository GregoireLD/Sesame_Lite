package com.paris.duval.sesamelite

import android.app.Application
import com.paris.duval.sesamelite.data.db.AppDatabase
import com.paris.duval.sesamelite.geo.GeofenceManager
import com.paris.duval.sesamelite.migration.MigrationManager
import com.paris.duval.sesamelite.notification.NotificationHelper
import com.paris.duval.sesamelite.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SesameLiteApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
        NotificationHelper.createChannel(this)

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(this@SesameLiteApplication).accessCodeDao()
            MigrationManager.migrateIfNeeded(dao)
            GeofenceManager.registerAll(this@SesameLiteApplication, dao.getAll(), currentLocation = null)
        }
    }
}
