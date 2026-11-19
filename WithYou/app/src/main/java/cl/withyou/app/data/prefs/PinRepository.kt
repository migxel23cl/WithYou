package cl.withyou.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cl.withyou.app.domain.security.StoredPin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** PIN del cuidador (Repository de MVVM): guarda solo la sal y el hash, nunca el PIN. */
interface PinRepository {

    /** null si no hay PIN configurado. */
    val storedPin: Flow<StoredPin?>

    suspend fun save(pin: StoredPin)

    suspend fun clear()
}

class DataStorePinRepository(private val dataStore: DataStore<Preferences>) : PinRepository {

    override val storedPin: Flow<StoredPin?> = dataStore.data.map { stored ->
        val salt = stored[SALT]
        val hash = stored[HASH]
        if (salt != null && hash != null) StoredPin(salt, hash) else null
    }

    override suspend fun save(pin: StoredPin) {
        dataStore.edit {
            it[SALT] = pin.salt
            it[HASH] = pin.hash
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(SALT)
            it.remove(HASH)
        }
    }

    private companion object {
        val SALT = stringPreferencesKey("pin_salt")
        val HASH = stringPreferencesKey("pin_hash")
    }
}
