package com.example.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.readerDataStore.data.map { prefs ->
        ReaderSettings(
            fontSize = prefs[KEY_FONT_SIZE] ?: 18,
            lineHeightMultiplier = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
            theme = runCatching { ReaderTheme.valueOf(prefs[KEY_THEME] ?: ReaderTheme.LIGHT.name) }
                .getOrDefault(ReaderTheme.LIGHT),
            useSerif = prefs[KEY_SERIF] ?: true
        )
    }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            val current = ReaderSettings(
                fontSize = prefs[KEY_FONT_SIZE] ?: 18,
                lineHeightMultiplier = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
                theme = runCatching { ReaderTheme.valueOf(prefs[KEY_THEME] ?: ReaderTheme.LIGHT.name) }
                    .getOrDefault(ReaderTheme.LIGHT),
                useSerif = prefs[KEY_SERIF] ?: true
            )
            val next = transform(current)
            prefs[KEY_FONT_SIZE] = next.fontSize
            prefs[KEY_LINE_HEIGHT] = next.lineHeightMultiplier
            prefs[KEY_THEME] = next.theme.name
            prefs[KEY_SERIF] = next.useSerif
        }
    }

    private companion object {
        val KEY_FONT_SIZE = intPreferencesKey("font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_SERIF = booleanPreferencesKey("serif")
    }
}
