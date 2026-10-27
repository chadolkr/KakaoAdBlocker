package com.example.kakaoadblocker.data

/** Extracts chat room information from the texts of a KakaoTalk chat list row. */
object ChatListParser {

    // Timestamps ("오후 3:21", "어제", "10월 3일", "2026. 1. 5.") and unread/member counts ("3", "300+")
    private val META_TEXT = Regex(
        "^(?:(?:오전|오후|AM|PM)?\\s*\\d{1,2}:\\d{2}(?:\\s*(?:AM|PM))?|어제|그저께|Yesterday|" +
            "\\d{1,2}월\\s*\\d{1,2}일|\\d{4}\\.\\s*\\d{1,2}\\.\\s*\\d{1,2}\\.?|\\d+\\+?)$",
        RegexOption.IGNORE_CASE
    )

    fun isMetaText(text: String): Boolean = META_TEXT.matches(text.trim())

    /**
     * Returns the chat room title of a row whose last message preview is [preview].
     * [rowTexts] are the row's texts in layout order; the title is the first one that is not the preview or metadata.
     */
    fun findTitle(rowTexts: List<String>, preview: String): String? =
        rowTexts
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != preview.trim() && !isMetaText(it) }
}
