package no.skiltvarsler.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.skiltvarsler.BuildConfig
import no.skiltvarsler.matcher.AlertSettings

private val Context.dataStore by preferencesDataStore("skilt_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AlertSettings> = context.dataStore.data.map { prefs ->
        AlertSettings(
            speedCamera = prefs[Keys.speedCamera] ?: true,
            speedLimit = prefs[Keys.speedLimit] ?: true,
            sectionAtk = prefs[Keys.sectionAtk] ?: true,
            toll = prefs[Keys.toll] ?: true,
            wildlife = prefs[Keys.wildlife] ?: true,
            railway = prefs[Keys.railway] ?: true,
            ferry = prefs[Keys.ferry] ?: true,
            stop = prefs[Keys.stop] ?: true,
            yield = prefs[Keys.yield] ?: true,
            hazard = prefs[Keys.hazard] ?: true,
            priorityRoad = prefs[Keys.priorityRoad] ?: false,
            municipality = prefs[Keys.municipality] ?: true,
        )
    }

    val tileBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.tileBaseUrl] ?: DEFAULT_TILE_BASE_URL
    }

    suspend fun setEnabled(keyName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val key = booleanPreferencesKey(keyName)
            prefs[key] = enabled
        }
    }

    suspend fun setTileBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.tileBaseUrl] = url
        }
    }

    private object Keys {
        val speedCamera = booleanPreferencesKey("speedCamera")
        val speedLimit = booleanPreferencesKey("speedLimit")
        val sectionAtk = booleanPreferencesKey("sectionAtk")
        val toll = booleanPreferencesKey("toll")
        val wildlife = booleanPreferencesKey("wildlife")
        val railway = booleanPreferencesKey("railway")
        val ferry = booleanPreferencesKey("ferry")
        val stop = booleanPreferencesKey("stop")
        val yield = booleanPreferencesKey("yield")
        val hazard = booleanPreferencesKey("hazard")
        val priorityRoad = booleanPreferencesKey("priorityRoad")
        val municipality = booleanPreferencesKey("municipality")
        val tileBaseUrl = stringPreferencesKey("tileBaseUrl")
    }

    companion object {
        const val DEFAULT_TILE_BASE_URL = BuildConfig.TILE_BASE_URL
    }
}
