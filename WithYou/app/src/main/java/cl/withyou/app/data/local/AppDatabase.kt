package cl.withyou.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local de la app. El cifrado con SQLCipher (RNF-05) se agrega en el paso 8.
 * El esquema se exporta a app/schemas/ para versionar sus cambios.
 */
@Database(entities = [ContactEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    companion object {
        private const val NAME = "withyou.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
