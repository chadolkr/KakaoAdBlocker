package com.example.kakaoadblocker.ui.main

import com.example.kakaoadblocker.data.BlockLogRepository
import com.example.kakaoadblocker.data.BlockedNotification
import com.example.kakaoadblocker.data.CleanupStatus
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_initiallyLoading() = runTest(dispatcher) {
    val viewModel = MainScreenViewModel(FakeBlockLogRepository())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.first())
  }

  @Test
  fun uiState_reflectsRepository() = runTest(dispatcher) {
    val repository = FakeBlockLogRepository()
    repository.addLog("쿠팡", "(광고) 특가", CleanupStatus.PENDING)
    repository.setAutoLeaveChatEnabled(true)
    val viewModel = MainScreenViewModel(repository)

    backgroundScope.launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    val state = viewModel.uiState.value as MainScreenUiState.Success
    assertEquals(1, state.totalBlockedCount)
    assertEquals(CleanupStatus.PENDING, state.blockedLogs.single().cleanupStatus)
    assertEquals(false, state.isAutoBlockChannelEnabled)
    assertEquals(true, state.isAutoLeaveChatEnabled)
  }
}

private class FakeBlockLogRepository : BlockLogRepository {
  private val logs = MutableStateFlow<List<BlockedNotification>>(emptyList())
  private val blockingEnabled = MutableStateFlow(true)
  private val autoBlock = MutableStateFlow(false)
  private val autoLeave = MutableStateFlow(false)

  override val blockedLogs: Flow<List<BlockedNotification>> = logs
  override val isBlockingEnabled: Flow<Boolean> = blockingEnabled
  override val isAutoBlockChannelEnabled: Flow<Boolean> = autoBlock
  override val isAutoLeaveChatEnabled: Flow<Boolean> = autoLeave

  override fun addLog(sender: String, text: String, cleanupStatus: CleanupStatus): String {
    val log = BlockedNotification(sender = sender, text = text, cleanupStatus = cleanupStatus)
    logs.value = listOf(log) + logs.value
    return log.id
  }

  override fun updateCleanupStatus(id: String, status: CleanupStatus) {
    logs.value = logs.value.map { if (it.id == id) it.copy(cleanupStatus = status) else it }
  }

  override fun removeLog(id: String) {
    logs.value = logs.value.filter { it.id != id }
  }

  override fun clearAllLogs() {
    logs.value = emptyList()
  }

  override fun setBlockingEnabled(enabled: Boolean) {
    blockingEnabled.value = enabled
  }

  override fun setAutoBlockChannelEnabled(enabled: Boolean) {
    autoBlock.value = enabled
  }

  override fun setAutoLeaveChatEnabled(enabled: Boolean) {
    autoLeave.value = enabled
  }
}
