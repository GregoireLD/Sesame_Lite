package com.duval.sesamelite.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * App-wide preferences backed by SharedPreferences and exposed as Compose state,
 * mirroring iOS @AppStorage semantics.
 *
 * Call init() once from SesameApplication.onCreate().
 */
object AppPrefs {

    /** Unlocks hidden developer features; toggled by long-pressing the version row. */
    val showHiddenFeatures = mutableStateOf(false)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("sesame_app_prefs", Context.MODE_PRIVATE)
        showHiddenFeatures.value = prefs!!.getBoolean("showHiddenFeatures", false)
    }

    fun toggleHiddenFeatures() {
        val next = !showHiddenFeatures.value
        showHiddenFeatures.value = next
        prefs?.edit()?.putBoolean("showHiddenFeatures", next)?.apply()
    }
}
