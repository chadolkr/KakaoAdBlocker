package com.example.kakaoadblocker.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

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
        if (packageName != KAKAO_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extract title (usually the sender or group chat name) and text (the message content)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Korean regulations require all advertising messages to start with "(광고)".
        // Check every text field because KakaoTalk may put the message in big text or MessagingStyle only.
        val adText = AdDetector.findAd(collectTexts(notification, title, text)) ?: return

        // Cancel/dismiss the notification so the user doesn't see it
        cancelNotification(sbn.key)

        val sender = title.ifEmpty { "KakaoTalk Channel" }
        val shouldCleanup = title.isNotEmpty() &&
            KakaoCleanupAccessibilityService.isRunning &&
            (prefs.getBoolean("auto_block_channel", false) || prefs.getBoolean("auto_leave_chat", false))

        // Log the blocked ad message
        val logId = repository.addLog(
            sender = sender,
            text = text.ifEmpty { adText.toString() },
            cleanupStatus = if (shouldCleanup) CleanupStatus.PENDING else CleanupStatus.NONE
        )

        if (shouldCleanup) {
            val queued = CleanupQueue.enqueue(CleanupTask(title, logId, notification.contentIntent))
            // Another ad from the same channel is already being cleaned up
            if (!queued) repository.updateCleanupStatus(logId, CleanupStatus.NONE)
        }

        Log.d("KakaoAdBlocker", "Blocked notification from '$sender' containing: '$adText'")
    }

    private fun collectTexts(notification: Notification, title: String, text: String): List<CharSequence?> {
        val extras = notification.extras
        val texts = mutableListOf<CharSequence?>(
            title,
            text,
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        )
        // Inbox/Messaging styles also carry earlier messages; only the newest one belongs to this notification
        texts.add(extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull())
        texts.add(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.lastOrNull()
                ?.text
        )
        return texts
    }

    companion object {
        const val KAKAO_PACKAGE = "com.kakao.talk"
    }
}
