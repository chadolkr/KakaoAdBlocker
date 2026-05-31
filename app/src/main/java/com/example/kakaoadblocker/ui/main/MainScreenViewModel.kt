package com.example.kakaoadblocker.ui.main

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakaoadblocker.data.BlockLogRepository
import com.example.kakaoadblocker.data.BlockedNotification
import com.example.kakaoadblocker.data.KakaoAdBlockerService
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
        val isBlockingEnabled: Boolean
    ) : MainScreenUiState

    data class Error(val throwable: Throwable) : MainScreenUiState
}

class MainScreenViewModel(private val repository: BlockLogRepository) : ViewModel() {

    private val _isServiceEnabled = MutableStateFlow(false)

    val uiState: StateFlow<MainScreenUiState> = combine(
        _isServiceEnabled,
        repository.blockedLogs,
        repository.isBlockingEnabled
    ) { enabled, logs, blockingEnabled ->
        MainScreenUiState.Success(
            isServiceEnabled = enabled,
            blockedLogs = logs,
            totalBlockedCount = logs.size,
            isBlockingEnabled = blockingEnabled
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

    fun checkServicePermission(context: Context) {
        viewModelScope.launch {
            val enabled = isNotificationServiceEnabled(context)
            _isServiceEnabled.value = enabled
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
}
