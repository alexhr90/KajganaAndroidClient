package com.alexhr90.muabet.data.post

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.drafts by preferencesDataStore(name = "drafts")

/** A message being written: kept until it is sent, so nothing typed is ever lost. */
@Serializable
data class Draft(val title: String = "", val to: String = "", val text: String = "", val savedAt: Long = 0)

/** Drafts by what they answer ("reply:123", "thread:45", "message:67"), on the phone only. */
@Singleton
class DraftStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(key: String): Draft? {
        val raw = context.drafts.data.first()[stringPreferencesKey(key)] ?: return null
        return runCatching { json.decodeFromString(Draft.serializer(), raw) }.getOrNull()
    }

    suspend fun put(key: String, draft: Draft) {
        context.drafts.edit { prefs ->
            if (draft.title.isBlank() && draft.text.isBlank() && draft.to.isBlank()) {
                prefs.remove(stringPreferencesKey(key))
            } else {
                prefs[stringPreferencesKey(key)] = json.encodeToString(Draft.serializer(), draft.copy(savedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun clear(key: String) {
        context.drafts.edit { it.remove(stringPreferencesKey(key)) }
    }

    /** Removes every draft (logout). */
    suspend fun clearAll() {
        context.drafts.edit { it.clear() }
    }
}
