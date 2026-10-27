package com.example.kakaoadblocker.data

import android.app.PendingIntent

/** A chat room that should be cleaned up (channel blocked and/or chat left) after an ad was detected. */
data class CleanupTask(
    val chatTitle: String,
    val logId: String,
    val contentIntent: PendingIntent?
)

/**
 * In-memory queue shared by [KakaoAdBlockerService] (producer) and
 * [KakaoCleanupAccessibilityService] (consumer). Both run in the app process.
 */
object CleanupQueue {
    private val tasks = ArrayDeque<CleanupTask>()

    /** Called on the main thread whenever a task is enqueued. */
    @Volatile
    var onTaskAdded: (() -> Unit)? = null

    /** Returns false if a task for the same chat room is already waiting. */
    fun enqueue(task: CleanupTask): Boolean {
        synchronized(this) {
            if (tasks.any { it.chatTitle == task.chatTitle }) return false
            tasks.addLast(task)
        }
        onTaskAdded?.invoke()
        return true
    }

    @Synchronized
    fun poll(): CleanupTask? = tasks.removeFirstOrNull()

    @Synchronized
    fun drain(): List<CleanupTask> = tasks.toList().also { tasks.clear() }
}
