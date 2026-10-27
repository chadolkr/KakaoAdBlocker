package com.example.kakaoadblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDetectorTest {

  @Test
  fun detectsStandardMarker() {
    assertTrue(AdDetector.isAd("(광고) 골드박스 특가"))
  }

  @Test
  fun detectsMarkerVariants() {
    listOf(
      "  (광고) 앞 공백",
      "(광고)붙여쓰기",
      "( 광고 ) 괄호 안 공백",
      "(광 고) 글자 사이 공백",
      "(광·고) 가운뎃점",
      "[광고] 대괄호",
      "【광고】 꺾쇠",
      "<광고> 홑화살괄호",
      "《광고》 겹화살괄호",
      "（광고） 전각 괄호",
      "［광고］ 전각 대괄호",
      "​(광고) 폭 없는 공백",
      "(​광고) 폭 없는 공백 안쪽",
      "🎁 (광고) 이모지 접두",
      "🎁️(광고) 변형 선택자 포함 이모지",
      "★(광고) 기호 접두",
      "[Web발신] (광고) 문자 게이트웨이",
      "[Web발신](광고)붙여쓰기",
    ).forEach { assertTrue("expected ad: $it", AdDetector.isAd(it)) }
  }

  @Test
  fun detectsMarkerOnLaterLine() {
    assertTrue(AdDetector.isAd("쿠팡\n(광고) 오늘의 특가"))
  }

  @Test
  fun ignoresMarkerBeyondFirstLines() {
    assertFalse(AdDetector.isAd("1\n2\n3\n(광고) 네 번째 줄"))
  }

  @Test
  fun ignoresNormalMessages() {
    listOf(
      null,
      "",
      "   ",
      "광고 보셨어요?",
      "이거 (광고) 아니야?",
      "광고) 여는 괄호 없음",
      "(광고문의) 다른 단어",
      "어제 본 광고 진짜 웃기더라",
      "(공지) 모임 일정",
    ).forEach { assertFalse("expected not ad: $it", AdDetector.isAd(it)) }
  }

  @Test
  fun findAdReturnsFirstMatchingText() {
    assertEquals("[광고] 본문", AdDetector.findAd(listOf("채널명", null, "[광고] 본문")))
    assertNull(AdDetector.findAd(listOf("채널명", "안녕하세요")))
  }
}
