package cl.withyou.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Base de datos local de la app, **cifrada** con SQLCipher (AES-256, RNF-05). La clave está
 * protegida por el Android Keystore ([DatabasePassphrase]). El esquema se exporta a app/schemas/.
 */
@Database(entities = [ContactEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    companion object {
        /** Nombre nuevo: la base de datos anterior a la versión cifrada se llamaba "withyou.db". */
        const val NAME = "withyou-secure.db"
        private const val LEGACY_PLAIN_NAME = "withyou.db"

        fun create(context: Context): AppDatabase {
            val appContext = context.applicationContext
            System.loadLibrary("sqlcipher")
            val passphrase = DatabasePassphrase(appContext)
            val key = passphrase.get()
            // Datos que no se pueden descifrar (clave nueva) o la base antigua sin cifrar: se borran,
            // para que no quede ningún dato personal guardado en texto plano.
            if (passphrase.isNew) appContext.deleteDatabase(NAME)
            appContext.deleteDatabase(LEGACY_PLAIN_NAME)
            return Room.databaseBuilder(appContext, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .build()
        }
    }
}
