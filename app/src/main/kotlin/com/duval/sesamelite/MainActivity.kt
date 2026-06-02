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

    private val pendingImportUriState = mutableStateOf<String?>(null)
    private val pendingEntryIdState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        val onboardingDone = runBlocking {
            dataStore.data.map { it[ONBOARDING_DONE] ?: false }.first()
        }

        setContent {
            SesameLiteTheme {
                val navController = rememberNavController()
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }
                val pendingImportUri by pendingImportUriState
                val pendingEntryId by pendingEntryIdState

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
                            onImportConsumed = { pendingImportUriState.value = null },
                            pendingEntryId = pendingEntryId,
                            onEntryIdConsumed = { pendingEntryIdState.value = null },
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        val entryId = intent.getStringExtra(NotificationHelper.EXTRA_ENTRY_ID)
        if (entryId != null) {
            pendingEntryIdState.value = entryId
            return
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data ?: return
            if (data.scheme == "sesame" && data.host == "import") {
                pendingImportUriState.value = data.toString()
            }
        }
    }
}
