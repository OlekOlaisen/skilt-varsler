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
import no.skiltvarsler.matcher.SignCatalog
import no.skiltvarsler.matcher.SignGroup

private val Context.dataStore by preferencesDataStore("skilt_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AlertSettings> = context.dataStore.data.map { prefs ->
        val categoryFallback = CATEGORY_KEYS.associate { keyName ->
            keyName to (prefs[booleanPreferencesKey(keyName)] ?: defaultForCategory(keyName))
        }
        val byId = SignCatalog.all.associate { sign ->
            val stored = prefs[booleanPreferencesKey(signKey(sign.id))]
            sign.id to (stored ?: categoryFallback[sign.categoryKey] ?: sign.defaultEnabled)
        }
        AlertSettings(
            byId = byId,
            categoryFallback = categoryFallback,
            alertsMuted = prefs[Keys.alertsMuted] ?: false,
        )
    }

    val tileBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.tileBaseUrl] ?: DEFAULT_TILE_BASE_URL
    }

    suspend fun setSignEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(signKey(id))] = enabled
        }
    }

    suspend fun setGroupEnabled(group: SignGroup, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            group.signs.forEach { sign ->
                prefs[booleanPreferencesKey(signKey(sign.id))] = enabled
            }
        }
    }

    suspend fun setAlertsMuted(muted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.alertsMuted] = muted
        }
    }

    suspend fun setTileBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.tileBaseUrl] = url
        }
    }

    private object Keys {
        val tileBaseUrl = stringPreferencesKey("tileBaseUrl")
        val alertsMuted = booleanPreferencesKey("alertsMuted")
    }

    companion object {
        const val DEFAULT_TILE_BASE_URL = BuildConfig.TILE_BASE_URL

        private val CATEGORY_KEYS = listOf(
            "speedCamera",
            "speedLimit",
            "sectionAtk",
            "toll",
            "wildlife",
            "railway",
            "ferry",
            "stop",
            "yield",
            "hazard",
            "priorityRoad",
            "municipality",
        )

        private fun signKey(id: String) = "sign:$id"

        private fun defaultForCategory(keyName: String): Boolean {
            return SignCatalog.all.firstOrNull { it.categoryKey == keyName }?.defaultEnabled ?: true
        }
    }
}
