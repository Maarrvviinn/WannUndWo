package de.marvin.wannundwo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedAccount(val email: String, val password: String, val displayName: String)

class UserPreferences(private val context: Context) {
    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val RECENT_LOCATIONS_KEY = stringPreferencesKey("recent_locations")
        val RECENT_TIMES_KEY = stringPreferencesKey("recent_times")
        val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts")

        private fun decodeList(s: String): List<String> =
            if (s.isBlank()) emptyList() else s.split("\n").filter { it.isNotBlank() }
        private fun encodeList(l: List<String>): String = l.joinToString("\n")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE_KEY] ?: true }
    val recentLocations: Flow<List<String>> = context.dataStore.data
        .map { decodeList(it[RECENT_LOCATIONS_KEY] ?: "") }
    val recentTimes: Flow<List<String>> = context.dataStore.data
        .map { decodeList(it[RECENT_TIMES_KEY] ?: "") }
    val savedAccounts: Flow<List<SavedAccount>> = context.dataStore.data
        .map { prefs ->
            decodeList(prefs[SAVED_ACCOUNTS_KEY] ?: "").mapNotNull { line ->
                val p = line.split("|||")
                when (p.size) {
                    2 -> SavedAccount(p[0], "", p[1])
                    // Backward compatibility for old plaintext format: email|||password|||displayName
                    3 -> SavedAccount(p[0], p[1], p[2])
                    else -> null
                }
            }
        }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE_KEY] = enabled }
    }

    suspend fun addRecentLocation(location: String) {
        if (location.isBlank()) return
        context.dataStore.edit { prefs ->
            val list = decodeList(prefs[RECENT_LOCATIONS_KEY] ?: "").toMutableList()
            list.remove(location)
            list.add(0, location)
            prefs[RECENT_LOCATIONS_KEY] = encodeList(list.take(5))
        }
    }

    suspend fun addRecentTime(time: String) {
        if (time.isBlank()) return
        context.dataStore.edit { prefs ->
            val list = decodeList(prefs[RECENT_TIMES_KEY] ?: "").toMutableList()
            list.remove(time)
            list.add(0, time)
            prefs[RECENT_TIMES_KEY] = encodeList(list.take(5))
        }
    }

    suspend fun saveAccount(email: String, displayName: String, password: String? = null) {
        context.dataStore.edit { prefs ->
            val existing = decodeList(prefs[SAVED_ACCOUNTS_KEY] ?: "").mapNotNull { line ->
                val p = line.split("|||")
                when (p.size) {
                    2 -> SavedAccount(p[0], "", p[1])
                    3 -> SavedAccount(p[0], p[1], p[2])
                    else -> null
                }
            }

            val old = existing.firstOrNull { it.email == email }
            val effectivePassword = if (!password.isNullOrBlank()) password else old?.password.orEmpty()
            val entry = SavedAccount(email, effectivePassword, displayName)

            val list = existing.filter { it.email != email }.toMutableList()
            list.add(0, entry)

            val encoded = list.take(5).joinToString("\n") {
                "${it.email}|||${it.password}|||${it.displayName}"
            }
            prefs[SAVED_ACCOUNTS_KEY] = encoded
        }
    }

    suspend fun removeAccount(email: String) {
        context.dataStore.edit { prefs ->
            val list = decodeList(prefs[SAVED_ACCOUNTS_KEY] ?: "")
                .filter { !it.startsWith("$email|||") }
            prefs[SAVED_ACCOUNTS_KEY] = encodeList(list)
        }
    }
}

