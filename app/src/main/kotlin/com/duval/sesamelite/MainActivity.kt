package com.duval.sesamelite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.rememberNavController
import com.duval.sesamelite.crypto.KeyAvailabilityMonitor
import com.duval.sesamelite.data.db.AppDatabase
import com.duval.sesamelite.geo.GeofenceManager
import com.duval.sesamelite.notification.NotificationHelper
import com.duval.sesamelite.ui.nav.NavRoutes
import com.duval.sesamelite.ui.nav.SesameNavGraph
import com.duval.sesamelite.ui.onboarding.OnboardingScreen
import com.duval.sesamelite.ui.theme.SesameLiteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val android.content.Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "sesame_prefs")

private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

class MainActivity : ComponentActivity() {

    private val keyMonitor = KeyAvailabilityMonitor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val onboardingDone = runBlocking {
            dataStore.data.map { it[ONBOARDING_DONE] ?: false }.first()
        }

        setContent {
            SesameLiteTheme {
                val navController = rememberNavController()
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }
                var pendingImportUri by remember { mutableStateOf(extractSesameUri(intent)) }

                when {
                    showOnboarding -> {
                        OnboardingScreen(onComplete = {
                            showOnboarding = false
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStore.edit { it[ONBOARDING_DONE] = true }
                            }
                        })
                    }
                    else -> {
                        SesameNavGraph(
                            navController = navController,
                            pendingImportUri = pendingImportUri,
                            onImportConsumed = { pendingImportUri = null },
                            onReplayOnboarding = { showOnboarding = true },
                            onResetAllData = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = AppDatabase.get(this@MainActivity).accessCodeDao()
                                    keyMonitor.resetAllData(dao)
                                    GeofenceManager.registerAll(this@MainActivity, emptyList(), null)
                                }
                            }
                        )
                    }
                }
            }
        }

        handleNotificationIntent(intent)  // no-op currently
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun extractSesameUri(intent: Intent?): String? {
        intent ?: return null
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data ?: return null
            if (data.scheme == "sesame" && data.host == "import") {
                return data.toString()
            }
        }
        return null
    }

    private fun handleNotificationIntent(intent: Intent?) {
        // Notification taps open MainActivity with the entry ID extra.
        // The activity is already set up; tapping navigates to the list
        // and the detail screen can be opened from there.
        // Deep-linking via sesame://import is handled in extractSesameUri.
    }
}
