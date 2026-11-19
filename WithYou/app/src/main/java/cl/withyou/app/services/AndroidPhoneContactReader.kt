package cl.withyou.app.services

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee el número elegido en la agenda. No requiere el permiso READ_CONTACTS (RNF-06): al elegir,
 * Android da a la app acceso temporal solo a ese contacto.
 */
class AndroidPhoneContactReader(context: Context) : PhoneContactReader {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): PhoneContact? = withContext(Dispatchers.IO) {
        try {
            resolver.query(Uri.parse(uri), arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)).orEmpty()
                    val phone = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)).orEmpty()
                    PhoneContact(name, phone)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin acceso al contacto elegido", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Contacto elegido con datos inesperados", e)
            null
        }
    }

    private companion object {
        const val TAG = "WithYou.Contacts"
    }
}
