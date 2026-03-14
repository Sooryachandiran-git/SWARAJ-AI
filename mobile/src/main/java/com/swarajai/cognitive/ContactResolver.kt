package com.swarajai.cognitive

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

/**
 * Resolves contact names (English or Tamil) to phone numbers.
 * Essential for "Call Amma" or "WhatsApp Doctor" intents.
 */
object ContactResolver {
    private const val TAG = "SwarajContacts"

    fun resolveContact(context: Context, name: String): String? {
        if (name.isBlank()) return null
        
        Log.d(TAG, "🔍 Resolving contact: $name")
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        val cursor = contentResolver.query(uri, projection, null, null, null)
        
        var bestMatch: String? = null
        var foundExact = false

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val displayName = it.getString(nameIndex)
                val number = it.getString(numberIndex)

                // Exact match (case insensitive)
                if (displayName.equals(name, ignoreCase = true)) {
                    Log.i(TAG, "✅ Exact match found: $displayName -> $number")
                    bestMatch = number
                    foundExact = true
                    break
                }

                // Fuzzy match: if contact name contains the query (e.g. "Amma Home" matches "Amma")
                if (displayName.contains(name, ignoreCase = true)) {
                    Log.d(TAG, "🤔 Fuzzy match: $displayName")
                    bestMatch = number
                }
            }
        }

        if (!foundExact && bestMatch != null) {
            Log.i(TAG, "✅ Using best fuzzy match: $bestMatch")
        }

        return bestMatch
    }
}
