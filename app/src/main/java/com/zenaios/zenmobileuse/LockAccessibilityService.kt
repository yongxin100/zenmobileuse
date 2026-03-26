package com.zenaios.zenmobileuse

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {
    private var lastUsageCheckAt = 0L
    private var lastOverlayLaunchAt = 0L
    private val homePackageName: String? by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(homeIntent, 0)
        resolved?.activityInfo?.packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastUsageCheckAt >= 30_000L) {
            lastUsageCheckAt = now
            if (checkUsageStatsPermission(this)) {
                val usedMillis = getDailyUsageStats(this).totalUsageTime
                UsageLimitManager.updateLockState(this, usedMillis)
            }
        }
        if (!UsageLimitManager.isLocked(this)) return
        if (isAllowedPackage(packageName)) return
        if (now - lastOverlayLaunchAt < 700L) return
        lastOverlayLaunchAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    private fun isAllowedPackage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName) return true
        if (packageName == homePackageName) return true
        if (packageName.contains("print", ignoreCase = true)) return true
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        if (packageName == defaultIme) return true
        if (packageName == "com.android.inputmethod.latin") return true
        if (packageName.startsWith("com.google.android.inputmethod")) return true
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val isSystemApp = appInfo != null && (
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )
        if (isSystemApp) return true
        return false
    }
}
