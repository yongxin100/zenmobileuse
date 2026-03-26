package com.zenaios.zenmobileuse

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsageLimitManager {
    private const val PREFS_NAME = "zen_prefs"
    private const val KEY_LIMIT_DATE = "usage_limit_date"
    private const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
    private const val KEY_TEMP_EXTRA_MINUTES = "temp_extra_minutes"
    private const val KEY_LOCKED = "usage_locked"
    private const val KEY_UNLOCK_TODAY = "unlock_today"
    private const val KEY_ADMIN_PASSWORD = "admin_password"
    private const val DEFAULT_ADMIN_PASSWORD = "zen"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun ensureToday(context: Context) {
        val sharedPrefs = prefs(context)
        val today = todayKey()
        val saved = sharedPrefs.getString(KEY_LIMIT_DATE, null)
        val editor = sharedPrefs.edit()
        if (saved != today) {
            editor.putString(KEY_LIMIT_DATE, today)
            editor.putInt(KEY_TEMP_EXTRA_MINUTES, 0)
            editor.putBoolean(KEY_LOCKED, false)
            editor.putBoolean(KEY_UNLOCK_TODAY, false)
        }
        if (!sharedPrefs.contains(KEY_DAILY_LIMIT_MINUTES)) {
            editor.putInt(KEY_DAILY_LIMIT_MINUTES, 180)
        }
        editor.apply()
    }

    fun getDailyLimitMinutes(context: Context): Int {
        ensureToday(context)
        return prefs(context).getInt(KEY_DAILY_LIMIT_MINUTES, 180)
    }

    fun updateDailyLimitMinutes(context: Context, minutes: Int) {
        ensureToday(context)
        prefs(context).edit().putInt(KEY_DAILY_LIMIT_MINUTES, minutes).apply()
    }

    fun getTempExtraMinutes(context: Context): Int {
        ensureToday(context)
        return prefs(context).getInt(KEY_TEMP_EXTRA_MINUTES, 0)
    }

    fun addTempExtraMinutes(context: Context, minutes: Int) {
        ensureToday(context)
        val sharedPrefs = prefs(context)
        val total = sharedPrefs.getInt(KEY_TEMP_EXTRA_MINUTES, 0) + minutes
        sharedPrefs.edit().putInt(KEY_TEMP_EXTRA_MINUTES, total).apply()
    }

    fun getTotalQuotaMillis(context: Context): Long {
        if (isUnlockedToday(context)) return Long.MAX_VALUE
        val totalMinutes = getDailyLimitMinutes(context) + getTempExtraMinutes(context)
        return totalMinutes * 60_000L
    }

    fun getRemainingMillis(context: Context, usedMillis: Long): Long {
        return getTotalQuotaMillis(context) - usedMillis
    }

    fun isLocked(context: Context): Boolean {
        ensureToday(context)
        if (isUnlockedToday(context)) return false
        return prefs(context).getBoolean(KEY_LOCKED, false)
    }

    fun updateLockState(context: Context, usedMillis: Long): Boolean {
        ensureToday(context)
        if (isUnlockedToday(context)) {
            prefs(context).edit().putBoolean(KEY_LOCKED, false).apply()
            return false
        }
        val shouldLock = getRemainingMillis(context, usedMillis) < 0
        prefs(context).edit().putBoolean(KEY_LOCKED, shouldLock).apply()
        return shouldLock
    }

    fun unlockToday(context: Context) {
        ensureToday(context)
        prefs(context).edit()
            .putBoolean(KEY_UNLOCK_TODAY, true)
            .putBoolean(KEY_LOCKED, false)
            .apply()
    }

    fun relockToday(context: Context, usedMillis: Long) {
        ensureToday(context)
        prefs(context).edit()
            .putBoolean(KEY_UNLOCK_TODAY, false)
            .apply()
        updateLockState(context, usedMillis)
    }

    fun isUnlockedToday(context: Context): Boolean {
        ensureToday(context)
        return prefs(context).getBoolean(KEY_UNLOCK_TODAY, false)
    }

    fun getAdminPassword(context: Context): String {
        val sharedPrefs = prefs(context)
        val stored = sharedPrefs.getString(KEY_ADMIN_PASSWORD, null)
        if (stored.isNullOrEmpty()) {
            sharedPrefs.edit().putString(KEY_ADMIN_PASSWORD, DEFAULT_ADMIN_PASSWORD).apply()
            return DEFAULT_ADMIN_PASSWORD
        }
        return stored
    }

    fun updateAdminPassword(context: Context, newPassword: String) {
        prefs(context).edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply()
    }
}
