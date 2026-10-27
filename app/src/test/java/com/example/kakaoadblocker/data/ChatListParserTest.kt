package com.example.kakaoadblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListParserTest {

  @Test
  fun findsTitleBeforePreview() {
    assertEquals("쿠팡", ChatListParser.findTitle(listOf("쿠팡", "(광고) 오늘의 특가", "오후 3:21", "2"), "(광고) 오늘의 특가"))
  }

  @Test
  fun skipsLeadingMetadata() {
    assertEquals("배달의민족", ChatListParser.findTitle(listOf("어제", "300+", "배달의민족", "(광고) 쿠폰"), "(광고) 쿠폰"))
  }

  @Test
  fun returnsNullWithoutTitle() {
    assertNull(ChatListParser.findTitle(listOf("(광고) 쿠폰", "10월 3일"), "(광고) 쿠폰"))
  }

  @Test
  fun recognizesMetaTexts() {
    listOf("오전 9:05", "오후 12:30", "9:05 AM", "어제", "Yesterday", "10월 3일", "2026. 1. 5.", "3", "999+")
      .forEach { assertTrue("expected meta: $it", ChatListParser.isMetaText(it)) }
    listOf("쿠팡", "11번가", "오후의 카페", "3시 모임")
      .forEach { assertFalse("expected title: $it", ChatListParser.isMetaText(it)) }
  }
}
