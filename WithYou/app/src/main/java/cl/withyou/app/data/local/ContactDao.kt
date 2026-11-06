package cl.withyou.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    /** Se actualiza sola cada vez que cambia la tabla. Ordenados por nombre, sin importar mayúsculas. */
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    /** Crea el contacto si su id es 0; si ya existe, lo actualiza. */
    @Upsert
    suspend fun upsert(contact: ContactEntity): Long

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
