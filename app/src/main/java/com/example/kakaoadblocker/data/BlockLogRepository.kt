package com.example.kakaoadblocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class CleanupStatus {
    NONE,          // Auto cleanup was not requested
    PENDING,       // Waiting for the accessibility service to clean up the chat
    DONE,          // Channel blocked and/or chat left as configured
    PARTIAL,       // Chat left, but blocking the channel failed
    NOT_CHANNEL,   // Skipped because the chat room is not a channel chat
    FAILED
}

data class BlockedNotification(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cleanupStatus: CleanupStatus = CleanupStatus.NONE
)

interface BlockLogRepository {
    val blockedLogs: Flow<List<BlockedNotification>>
    fun addLog(sender: String, text: String, cleanupStatus: CleanupStatus = CleanupStatus.NONE): String
    fun updateCleanupStatus(id: String, status: CleanupStatus)
    fun removeLog(id: String)
    fun clearAllLogs()
    val isBlockingEnabled: Flow<Boolean>
    fun setBlockingEnabled(enabled: Boolean)
    val isAutoBlockChannelEnabled: Flow<Boolean>
    fun setAutoBlockChannelEnabled(enabled: Boolean)
    val isAutoLeaveChatEnabled: Flow<Boolean>
    fun setAutoLeaveChatEnabled(enabled: Boolean)
}

class SharedPreferencesBlockLogRepository(private val context: Context) : BlockLogRepository {
    private val prefs = context.getSharedPreferences("block_logs_pref", Context.MODE_PRIVATE)
    private val _blockedLogs = MutableStateFlow<List<BlockedNotification>>(emptyList())
    override val blockedLogs: Flow<List<BlockedNotification>> = _blockedLogs.asStateFlow()

    private val _isBlockingEnabled = MutableStateFlow(true)
    override val isBlockingEnabled: Flow<Boolean> = _isBlockingEnabled.asStateFlow()

    private val _isAutoBlockChannelEnabled = MutableStateFlow(false)
    override val isAutoBlockChannelEnabled: Flow<Boolean> = _isAutoBlockChannelEnabled.asStateFlow()

    private val _isAutoLeaveChatEnabled = MutableStateFlow(false)
    override val isAutoLeaveChatEnabled: Flow<Boolean> = _isAutoLeaveChatEnabled.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "blocked_logs" -> loadLogs()
            "is_blocking_enabled" -> _isBlockingEnabled.value = prefs.getBoolean(key, true)
            "auto_block_channel" -> _isAutoBlockChannelEnabled.value = prefs.getBoolean(key, false)
            "auto_leave_chat" -> _isAutoLeaveChatEnabled.value = prefs.getBoolean(key, false)
        }
    }

    init {
        _isBlockingEnabled.value = prefs.getBoolean("is_blocking_enabled", true)
        _isAutoBlockChannelEnabled.value = prefs.getBoolean("auto_block_channel", false)
        _isAutoLeaveChatEnabled.value = prefs.getBoolean("auto_leave_chat", false)
        loadLogs()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun setBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_blocking_enabled", enabled).apply()
        _isBlockingEnabled.value = enabled
    }

    override fun setAutoBlockChannelEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_block_channel", enabled).apply()
        _isAutoBlockChannelEnabled.value = enabled
    }

    override fun setAutoLeaveChatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_leave_chat", enabled).apply()
        _isAutoLeaveChatEnabled.value = enabled
    }

    @Synchronized
    private fun loadLogs() {
        val jsonStr = prefs.getString("blocked_logs", null) ?: "[]"
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<BlockedNotification>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(BlockedNotification(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    sender = obj.optString("sender", ""),
                    text = obj.optString("text", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    cleanupStatus = runCatching {
                        CleanupStatus.valueOf(obj.optString("cleanupStatus", CleanupStatus.NONE.name))
                    }.getOrDefault(CleanupStatus.NONE)
                ))
            }
            list.sortByDescending { it.timestamp }
            _blockedLogs.value = list
        } catch (e: Exception) {
            _blockedLogs.value = emptyList()
        }
    }

    @Synchronized
    private fun saveLogs(list: List<BlockedNotification>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("sender", item.sender)
                obj.put("text", item.text)
                obj.put("timestamp", item.timestamp)
                obj.put("cleanupStatus", item.cleanupStatus.name)
                array.put(obj)
            }
            prefs.edit().putString("blocked_logs", array.toString()).apply()
            _blockedLogs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    override fun addLog(sender: String, text: String, cleanupStatus: CleanupStatus): String {
        val currentList = _blockedLogs.value.toMutableList()
        val newLog = BlockedNotification(sender = sender, text = text, cleanupStatus = cleanupStatus)
        currentList.add(0, newLog)
        saveLogs(currentList)
        return newLog.id
    }

    @Synchronized
    override fun updateCleanupStatus(id: String, status: CleanupStatus) {
        val currentList = _blockedLogs.value.map { if (it.id == id) it.copy(cleanupStatus = status) else it }
        saveLogs(currentList)
    }

    override fun removeLog(id: String) {
        val currentList = _blockedLogs.value.filter { it.id != id }
        saveLogs(currentList)
    }

    override fun clearAllLogs() {
        saveLogs(emptyList())
    }
}
