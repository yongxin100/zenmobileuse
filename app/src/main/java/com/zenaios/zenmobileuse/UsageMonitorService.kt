package com.zenaios.zenmobileuse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class UsageMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINA)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                isTtsReady = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            startForeground(NOTIFICATION_ID, buildNotification("正在监测今日使用额度"))
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                while (isActive) {
                    runCatching { checkUsageAndUpdateLock() }
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun checkUsageAndUpdateLock() {
        UsageLimitManager.ensureToday(this)
        if (!checkUsageStatsPermission(this)) return
        val usedMillis = withContext(Dispatchers.Default) { getDailyUsageStats(this@UsageMonitorService).totalUsageTime }
        val locked = UsageLimitManager.updateLockState(this, usedMillis)
        val notificationText = if (locked) "已超出今日额度，非白名单应用不可用" else "正在监测今日使用额度"
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(notificationText))
        if (UsageLimitManager.shouldSendUsageReminder(this)) {
            val reminder = NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("zenA+ 使用提醒")
                .setContentText("当前已使用 ${formatTime(usedMillis)}")
                .setAutoCancel(true)
                .build()
            notificationManager.notify(REMINDER_NOTIFICATION_ID, reminder)
            val voiceText = if (locked) {
                "提醒你，手机使用时间已经超过今天上限。"
            } else {
                "提醒你，当前手机使用时间是${formatTime(usedMillis)}。"
            }
            speakReminder(voiceText)
        }
    }

    private fun speakReminder(text: String) {
        val tts = textToSpeech ?: return
        if (!isTtsReady) return
        val queueMode = TextToSpeech.QUEUE_FLUSH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, queueMode, null, "usage_reminder_tts")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, queueMode, null)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("zenA+ 使用监测")
            .setContentText(contentText)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "zenA+使用监测",
                NotificationManager.IMPORTANCE_LOW
            )
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "zenA+使用提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(reminderChannel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "zen_usage_monitor"
        private const val REMINDER_CHANNEL_ID = "zen_usage_reminder"
        private const val NOTIFICATION_ID = 2001
        private const val REMINDER_NOTIFICATION_ID = 2002
        private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}
