package com.example.kakaoadblocker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kakaoadblocker.data.BlockedNotification
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.kakaoadblocker.ui.main.MainContent]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainContent(
        isServiceEnabled = true,
        isBlockingEnabled = true,
        blockedLogs = FAKE_DATA,
        totalBlockedCount = FAKE_DATA.size,
        onOpenSettings = {},
        onToggleBlocking = {},
        onRemoveLog = {},
        onClearAllLogs = {}
      )
    }
  }

  @Test
  fun blockedLogs_areDisplayed() {
    FAKE_DATA.forEach { composeTestRule.onNodeWithText(it.text).assertExists() }
  }
}

private val FAKE_DATA = listOf(
  BlockedNotification(sender = "Sample1", text = "(광고) Sample text 1"),
  BlockedNotification(sender = "Sample2", text = "(광고) Sample text 2")
)
