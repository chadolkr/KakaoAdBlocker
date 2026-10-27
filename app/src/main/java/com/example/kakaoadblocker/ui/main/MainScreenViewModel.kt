package com.example.kakaoadblocker.ui.main

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakaoadblocker.data.BlockLogRepository
import com.example.kakaoadblocker.data.BlockedNotification
import com.example.kakaoadblocker.data.KakaoAdBlockerService
import com.example.kakaoadblocker.data.KakaoCleanupAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState

    data class Success(
        val isServiceEnabled: Boolean,
        val blockedLogs: List<BlockedNotification>,
        val totalBlockedCount: Int,
        val isBlockingEnabled: Boolean,
        val isAccessibilityEnabled: Boolean = false,
        val isAutoBlockChannelEnabled: Boolean = false,
        val isAutoLeaveChatEnabled: Boolean = false
    ) : MainScreenUiState

    data class Error(val throwable: Throwable) : MainScreenUiState
}

class MainScreenViewModel(private val repository: BlockLogRepository) : ViewModel() {

    private val _isServiceEnabled = MutableStateFlow(false)
    private val _isAccessibilityEnabled = MutableStateFlow(false)

    private val cleanupSettings = combine(
        _isAccessibilityEnabled,
        repository.isAutoBlockChannelEnabled,
        repository.isAutoLeaveChatEnabled,
        ::Triple
    )

    val uiState: StateFlow<MainScreenUiState> = combine(
        _isServiceEnabled,
        repository.blockedLogs,
        repository.isBlockingEnabled,
        cleanupSettings
    ) { enabled, logs, blockingEnabled, (accessibilityEnabled, autoBlock, autoLeave) ->
        MainScreenUiState.Success(
            isServiceEnabled = enabled,
            blockedLogs = logs,
            totalBlockedCount = logs.size,
            isBlockingEnabled = blockingEnabled,
            isAccessibilityEnabled = accessibilityEnabled,
            isAutoBlockChannelEnabled = autoBlock,
            isAutoLeaveChatEnabled = autoLeave
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenUiState.Loading
    )

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlockingEnabled(enabled)
        }
    }

    fun setAutoBlockChannelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoBlockChannelEnabled(enabled)
        }
    }

    fun setAutoLeaveChatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoLeaveChatEnabled(enabled)
        }
    }

    fun checkServicePermission(context: Context) {
        viewModelScope.launch {
            val enabled = isNotificationServiceEnabled(context)
            _isServiceEnabled.value = enabled
            _isAccessibilityEnabled.value = isAccessibilityServiceEnabled(context)
        }
    }

    fun removeLog(id: String) {
        viewModelScope.launch {
            repository.removeLog(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, KakaoAdBlockerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val cn = ComponentName(context, KakaoCleanupAccessibilityService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return flat.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }
}
