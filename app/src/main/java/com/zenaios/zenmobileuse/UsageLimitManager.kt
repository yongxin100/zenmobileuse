package com.zenaios.zenmobileuse

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsageLimitManager {
    private const val PREFS_NAME = "zen_prefs"
    private const val KEY_LIMIT_DATE = "usage_limit_date"
    private const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
    private const val KEY_TEMP_RELEASE_GRANTED_MINUTES = "temp_release_granted_minutes"
    private const val KEY_TEMP_RELEASE_END_AT_MILLIS = "temp_release_end_at_millis"
    private const val KEY_LOCKED = "usage_locked"
    private const val KEY_UNLOCK_TODAY = "unlock_today"
    private const val KEY_ADMIN_PASSWORD = "admin_password"
    private const val DEFAULT_ADMIN_PASSWORD = "zhansheng"
    private const val KEY_ADMIN_PASSWORD_RESET_TO_DEFAULT_DONE = "admin_password_reset_to_default_done"
    private const val KEY_SERVER_AVAILABLE_MINUTES = "server_available_minutes_int"
    private const val KEY_USAGE_REMINDER_INTERVAL_MINUTES = "usage_reminder_interval_minutes"
    private const val KEY_USAGE_REMINDER_LAST_AT_MILLIS = "usage_reminder_last_at_millis"

    data class LimitDecision(
        val syncedAvailableMinutes: Int?,
        val currentUsageMillis: Long,
        val dailyLimitMinutes: Int,
        val temporaryReleaseGrantedMinutes: Int,
        val temporaryReleaseRemainingMinutes: Int,
        val temporaryReleaseRemainingMillis: Long,
        val effectiveDailyLimitMinutes: Int,
        val dailyRemainingMillis: Long,
        val lockBySyncedBalance: Boolean,
        val lockByDailyLimit: Boolean,
        val shouldLock: Boolean
    )

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
            editor.putInt(KEY_TEMP_RELEASE_GRANTED_MINUTES, 0)
            editor.putLong(KEY_TEMP_RELEASE_END_AT_MILLIS, 0L)
            editor.putBoolean(KEY_LOCKED, false)
            editor.putBoolean(KEY_UNLOCK_TODAY, false)
            editor.putLong(KEY_USAGE_REMINDER_LAST_AT_MILLIS, 0L)
        }
        if (!sharedPrefs.contains(KEY_DAILY_LIMIT_MINUTES)) {
            editor.putInt(KEY_DAILY_LIMIT_MINUTES, 180)
        }
        if (!sharedPrefs.contains(KEY_USAGE_REMINDER_INTERVAL_MINUTES)) {
            editor.putInt(KEY_USAGE_REMINDER_INTERVAL_MINUTES, 60)
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

    fun getTemporaryReleaseGrantedMinutes(context: Context): Int {
        ensureToday(context)
        return prefs(context).getInt(KEY_TEMP_RELEASE_GRANTED_MINUTES, 0)
    }

    fun getTemporaryReleaseRemainingMillis(context: Context): Long {
        ensureToday(context)
        val sharedPrefs = prefs(context)
        val granted = sharedPrefs.getInt(KEY_TEMP_RELEASE_GRANTED_MINUTES, 0)
        if (granted <= 0) return 0L
        val endAt = sharedPrefs.getLong(KEY_TEMP_RELEASE_END_AT_MILLIS, 0L)
        if (endAt <= 0L) return 0L
        return (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getTemporaryReleaseRemainingMinutes(context: Context): Int {
        val remainingMillis = getTemporaryReleaseRemainingMillis(context)
        if (remainingMillis <= 0L) return 0
        return ((remainingMillis + 59_999L) / 60_000L).toInt()
    }

    fun startTemporaryRelease(context: Context, addMinutes: Int, currentUsageMillis: Long) {
        ensureToday(context)
        val sharedPrefs = prefs(context)
        val currentRemaining = getTemporaryReleaseRemainingMinutes(context)
        val newGranted = currentRemaining + addMinutes
        val endAt = System.currentTimeMillis() + newGranted * 60_000L
        sharedPrefs.edit()
            .putInt(KEY_TEMP_RELEASE_GRANTED_MINUTES, newGranted)
            .putLong(KEY_TEMP_RELEASE_END_AT_MILLIS, endAt)
            .apply()
    }

    private fun clearTemporaryRelease(context: Context) {
        prefs(context).edit()
            .putInt(KEY_TEMP_RELEASE_GRANTED_MINUTES, 0)
            .putLong(KEY_TEMP_RELEASE_END_AT_MILLIS, 0L)
            .apply()
    }

    fun stopTemporaryRelease(context: Context, currentUsageMillis: Long) {
        ensureToday(context)
        clearTemporaryRelease(context)
        updateLockState(context, currentUsageMillis)
    }

    fun updateServerAvailableMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SERVER_AVAILABLE_MINUTES, minutes).apply()
    }

    fun getUsageReminderIntervalMinutes(context: Context): Int {
        ensureToday(context)
        return prefs(context).getInt(KEY_USAGE_REMINDER_INTERVAL_MINUTES, 60)
    }

    fun updateUsageReminderIntervalMinutes(context: Context, minutes: Int) {
        ensureToday(context)
        prefs(context).edit().putInt(KEY_USAGE_REMINDER_INTERVAL_MINUTES, minutes).apply()
    }

    fun shouldSendUsageReminder(context: Context): Boolean {
        ensureToday(context)
        val intervalMinutes = getUsageReminderIntervalMinutes(context)
        if (intervalMinutes <= 0) return false
        val now = System.currentTimeMillis()
        val sharedPrefs = prefs(context)
        val lastAt = sharedPrefs.getLong(KEY_USAGE_REMINDER_LAST_AT_MILLIS, 0L)
        val intervalMillis = intervalMinutes * 60_000L
        if (now - lastAt < intervalMillis) return false
        sharedPrefs.edit().putLong(KEY_USAGE_REMINDER_LAST_AT_MILLIS, now).apply()
        return true
    }

    fun getServerAvailableMinutes(context: Context): Int? {
        val sharedPrefs = prefs(context)
        return if (sharedPrefs.contains(KEY_SERVER_AVAILABLE_MINUTES)) {
            sharedPrefs.getInt(KEY_SERVER_AVAILABLE_MINUTES, 0)
        } else {
            null
        }
    }

    fun getEffectiveDailyLimitMinutes(context: Context): Int {
        return getDailyLimitMinutes(context)
    }

    fun evaluateLimit(context: Context, currentUsageMillis: Long): LimitDecision {
        ensureToday(context)
        val syncedAvailableMinutes = getServerAvailableMinutes(context)
        val dailyLimitMinutes = getDailyLimitMinutes(context)
        val temporaryReleaseGrantedMinutes = getTemporaryReleaseGrantedMinutes(context)
        val temporaryReleaseRemainingMillis = getTemporaryReleaseRemainingMillis(context)
        val temporaryReleaseRemainingMinutes = getTemporaryReleaseRemainingMinutes(context)
        if (temporaryReleaseGrantedMinutes > 0 && temporaryReleaseRemainingMillis <= 0L) {
            clearTemporaryRelease(context)
        }
        val normalizedGrantedMinutes = if (temporaryReleaseRemainingMillis > 0L) temporaryReleaseGrantedMinutes else 0
        val normalizedRemainingMinutes = if (temporaryReleaseRemainingMillis > 0L) temporaryReleaseRemainingMinutes else 0
        val normalizedRemainingMillis = if (temporaryReleaseRemainingMillis > 0L) temporaryReleaseRemainingMillis else 0L
        val effectiveDailyLimitMinutes = dailyLimitMinutes
        val dailyRemainingMillis = effectiveDailyLimitMinutes * 60_000L - currentUsageMillis
        val unlockedToday = isUnlockedToday(context)
        val temporaryReleaseActive = normalizedRemainingMillis > 0L
        val lockBySyncedBalance = !unlockedToday && !temporaryReleaseActive && syncedAvailableMinutes != null && syncedAvailableMinutes < 0
        val lockByDailyLimit = !unlockedToday && !temporaryReleaseActive && currentUsageMillis > effectiveDailyLimitMinutes * 60_000L
        return LimitDecision(
            syncedAvailableMinutes = syncedAvailableMinutes,
            currentUsageMillis = currentUsageMillis,
            dailyLimitMinutes = dailyLimitMinutes,
            temporaryReleaseGrantedMinutes = normalizedGrantedMinutes,
            temporaryReleaseRemainingMinutes = normalizedRemainingMinutes,
            temporaryReleaseRemainingMillis = normalizedRemainingMillis,
            effectiveDailyLimitMinutes = effectiveDailyLimitMinutes,
            dailyRemainingMillis = dailyRemainingMillis,
            lockBySyncedBalance = lockBySyncedBalance,
            lockByDailyLimit = lockByDailyLimit,
            shouldLock = lockBySyncedBalance || lockByDailyLimit
        )
    }

    fun getTotalQuotaMillis(context: Context): Long {
        if (isUnlockedToday(context)) return Long.MAX_VALUE
        val totalMinutes = getEffectiveDailyLimitMinutes(context)
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
        val shouldLock = evaluateLimit(context, usedMillis).shouldLock
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
        if (!sharedPrefs.getBoolean(KEY_ADMIN_PASSWORD_RESET_TO_DEFAULT_DONE, false)) {
            sharedPrefs.edit()
                .putString(KEY_ADMIN_PASSWORD, DEFAULT_ADMIN_PASSWORD)
                .putBoolean(KEY_ADMIN_PASSWORD_RESET_TO_DEFAULT_DONE, true)
                .apply()
            return DEFAULT_ADMIN_PASSWORD
        }
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
