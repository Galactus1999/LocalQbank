package com.localqbank.library

import android.content.SharedPreferences

/**
 * Backward-compatible SharedPreferences readers.
 * Older Rovex builds stored some numeric settings as Int/Long while newer builds
 * read them as Float/Long. Android's SharedPreferences throws ClassCastException
 * when the stored type differs, so numeric reads must migrate legacy values.
 */
object PrefsCompat {
    fun float(p: SharedPreferences, key: String, default: Float): Float {
        return try {
            p.getFloat(key, default)
        } catch (_: ClassCastException) {
            val raw = p.all[key]
            val value = when (raw) {
                is Number -> raw.toFloat()
                is String -> raw.toFloatOrNull()
                else -> null
            } ?: return default
            p.edit().putFloat(key, value).apply()
            value
        }
    }

    fun long(p: SharedPreferences, key: String, default: Long): Long {
        return try {
            p.getLong(key, default)
        } catch (_: ClassCastException) {
            val raw = p.all[key]
            val value = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            } ?: return default
            p.edit().putLong(key, value).apply()
            value
        }
    }
}
