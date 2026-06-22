package com.kreativesolutions.aivideodetector.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kreativesolutions.aivideodetector.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.apiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "api_settings"
)

class ApiSettings(private val context: Context) {
    val linkApiBaseUrl: Flow<String> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[KEY_LINK_API_BASE_URL]?.trim().orEmpty()
            .ifBlank { BuildConfig.LINK_API_BASE_URL.trim() }
    }

    val linkApiKey: Flow<String> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[KEY_LINK_API_KEY]?.trim().orEmpty()
            .ifBlank { BuildConfig.LINK_API_KEY.trim() }
    }

    suspend fun setLinkApiBaseUrl(value: String) {
        context.apiSettingsDataStore.edit { prefs ->
            prefs[KEY_LINK_API_BASE_URL] = value.trim()
        }
    }

    suspend fun setLinkApiKey(value: String) {
        context.apiSettingsDataStore.edit { prefs ->
            prefs[KEY_LINK_API_KEY] = value.trim()
        }
    }

    companion object {
        private val KEY_LINK_API_BASE_URL = stringPreferencesKey("link_api_base_url")
        private val KEY_LINK_API_KEY = stringPreferencesKey("link_api_key")
    }
}
