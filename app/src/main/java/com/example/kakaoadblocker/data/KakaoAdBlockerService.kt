package com.example.kakaoadblocker.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class KakaoAdBlockerService : NotificationListenerService() {

    private lateinit var repository: BlockLogRepository

    override fun onCreate() {
        super.onCreate()
        repository = SharedPreferencesBlockLogRepository(applicationContext)
        Log.d("KakaoAdBlocker", "Service created and repository initialized")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Check if blocking is enabled by user
        val prefs = getSharedPreferences("block_logs_pref", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_blocking_enabled", true)) {
            return
        }

        val packageName = sbn.packageName
        if (packageName != "com.kakao.talk") return

        val extras = sbn.notification?.extras ?: return
        
        // Extract title (usually the sender or group chat name) and text (the message content)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Korean regulations require all advertising messages to start with "(광고)"
        if (text.startsWith("(광고)") || title.startsWith("(광고)")) {
            // Cancel/dismiss the notification so the user doesn't see it
            cancelNotification(sbn.key)
            
            // Log the blocked ad message
            val sender = title.ifEmpty { "KakaoTalk Channel" }
            repository.addLog(sender, text)
            
            Log.d("KakaoAdBlocker", "Blocked notification from '$sender' containing: '$text'")
        }
    }
}
