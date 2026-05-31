package com.example.kakaoadblocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BlockedNotification(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface BlockLogRepository {
    val blockedLogs: Flow<List<BlockedNotification>>
    fun addLog(sender: String, text: String)
    fun removeLog(id: String)
    fun clearAllLogs()
    val isBlockingEnabled: Flow<Boolean>
    fun setBlockingEnabled(enabled: Boolean)
}

class SharedPreferencesBlockLogRepository(private val context: Context) : BlockLogRepository {
    private val prefs = context.getSharedPreferences("block_logs_pref", Context.MODE_PRIVATE)
    private val _blockedLogs = MutableStateFlow<List<BlockedNotification>>(emptyList())
    override val blockedLogs: Flow<List<BlockedNotification>> = _blockedLogs.asStateFlow()

    private val _isBlockingEnabled = MutableStateFlow(true)
    override val isBlockingEnabled: Flow<Boolean> = _isBlockingEnabled.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "blocked_logs") {
            loadLogs()
        } else if (key == "is_blocking_enabled") {
            _isBlockingEnabled.value = prefs.getBoolean("is_blocking_enabled", true)
        }
    }

    init {
        _isBlockingEnabled.value = prefs.getBoolean("is_blocking_enabled", true)
        loadLogs()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun setBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_blocking_enabled", enabled).apply()
        _isBlockingEnabled.value = enabled
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
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
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
                array.put(obj)
            }
            prefs.edit().putString("blocked_logs", array.toString()).apply()
            _blockedLogs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addLog(sender: String, text: String) {
        val currentList = _blockedLogs.value.toMutableList()
        val newLog = BlockedNotification(sender = sender, text = text)
        currentList.add(0, newLog)
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
