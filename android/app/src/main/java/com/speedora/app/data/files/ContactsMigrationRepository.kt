package com.speedora.app.data.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.speedora.app.core.model.FileCategory
import com.speedora.app.core.model.LocalFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports user-consented device contacts into standard vCard 3.0 (.vcf) format
 * for selective phone migration. Never accessed without explicit user action & READ_CONTACTS grant.
 */
class ContactsMigrationRepository(private val context: Context) {

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun countContacts(): Int = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext 0
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )?.use { it.count } ?: 0
    }

    suspend fun exportContactsToVcf(): LocalFileItem? = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext null

        val vcfBuilder = StringBuilder()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val grouped = LinkedHashMap<String, Pair<String, MutableList<String>>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty().ifBlank { "Contact" }
                val number = cursor.getString(numIdx)?.trim().orEmpty()
                val entry = grouped.getOrPut(id) { name to mutableListOf() }
                if (number.isNotBlank() && !entry.second.contains(number)) {
                    entry.second.add(number)
                }
            }
        }

        if (grouped.isEmpty()) return@withContext null

        grouped.values.forEach { (name, numbers) ->
            vcfBuilder.appendLine("BEGIN:VCARD")
            vcfBuilder.appendLine("VERSION:3.0")
            vcfBuilder.appendLine("FN:$name")
            numbers.forEach { num ->
                vcfBuilder.appendLine("TEL;TYPE=CELL:$num")
            }
            vcfBuilder.appendLine("END:VCARD")
        }

        val dir = File(context.cacheDir, "migration").apply { mkdirs() }
        val file = File(dir, "speedora_contacts_migration.vcf")
        file.writeText(vcfBuilder.toString(), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        LocalFileItem(
            id = "migration:contacts",
            uri = uri,
            name = file.name,
            sizeBytes = file.length(),
            mimeType = "text/vcard",
            category = FileCategory.DOCUMENTS,
            dateModifiedMillis = file.lastModified()
        )
    }
}
