package no.skiltvarsler.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.skiltvarsler.BuildConfig
import no.skiltvarsler.matcher.AlertPriority
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
                // Legacy toggle id before roundabout moved from fare 126 → påbud 406.
                ?: if (sign.id == "hazard:406") {
                    prefs[booleanPreferencesKey(signKey("hazard:126"))]
                } else {
                    null
                }
            sign.id to (stored ?: categoryFallback[sign.categoryKey] ?: sign.defaultEnabled)
        }
        val priorityOverrides = SignCatalog.all.mapNotNull { sign ->
            val stored = prefs[intPreferencesKey(priorityKey(sign.id))] ?: return@mapNotNull null
            val tier = AlertPriority.coerce(stored)
            if (tier == sign.defaultPriorityTier) {
                null
            } else {
                sign.id to tier
            }
        }.toMap()
        AlertSettings(
            byId = byId,
            categoryFallback = categoryFallback,
            priorityOverrides = priorityOverrides,
            alertsMuted = prefs[Keys.alertsMuted] ?: false,
            combineAlerts = prefs[Keys.combineAlerts] ?: true,
            repeatReminderSigns = prefs[Keys.repeatReminderSigns] ?: false,
        )
    }

    val tileBaseUrl: Flow<String> = context.dataStore.data.map {
        DEFAULT_TILE_BASE_URL
    }

    val autoStartTracking: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.autoStartTracking] ?: true
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

    /**
     * Persists a user priority override. Passing the catalog default removes the key
     * so the foundation tier applies again.
     */
    suspend fun setSignPriority(id: String, tier: Int) {
        val coerced = AlertPriority.coerce(tier)
        val defaultTier = SignCatalog.defaultPriorityTier(id)
        context.dataStore.edit { prefs ->
            val key = intPreferencesKey(priorityKey(id))
            if (coerced == defaultTier) {
                prefs.remove(key)
            } else {
                prefs[key] = coerced
            }
        }
    }

    suspend fun setAlertsMuted(muted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.alertsMuted] = muted
        }
    }

    suspend fun setCombineAlerts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.combineAlerts] = enabled
        }
    }

    suspend fun setRepeatReminderSigns(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.repeatReminderSigns] = enabled
        }
    }

    suspend fun setAutoStartTracking(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.autoStartTracking] = enabled
        }
    }

    private object Keys {
        val alertsMuted = booleanPreferencesKey("alertsMuted")
        val combineAlerts = booleanPreferencesKey("combineAlerts")
        val repeatReminderSigns = booleanPreferencesKey("repeatReminderSigns")
        val autoStartTracking = booleanPreferencesKey("autoStartTracking")
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
            "roadwork",
            "accident",
        )

        private fun signKey(id: String) = "sign:$id"

        private fun priorityKey(id: String) = "priority:$id"

        private fun defaultForCategory(keyName: String): Boolean {
            return SignCatalog.all.firstOrNull { it.categoryKey == keyName }?.defaultEnabled ?: true
        }
    }
}
