package com.example.kakaoadblocker.data

import java.text.Normalizer

/**
 * Detects Korean advertising messages.
 *
 * Korean regulations require ad messages to start with "(광고)", but senders often vary the marker
 * (full-width brackets, [광고], 【광고】, "( 광 고 )", leading emoji, "[Web발신]" prefix, zero-width chars).
 */
object AdDetector {

    private const val OPEN_BRACKETS = "(\\[{<【〔〈《「『"
    private const val CLOSE_BRACKETS = ")\\]}>】〕〉》」』"

    // Soft hyphen, zero-width spaces/joiners, direction marks, word joiner, BOM
    private val INVISIBLE_CHARS = Regex("[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\uFEFF]")

    private val AD_MARKER = Regex(
        // Leading whitespace, punctuation, symbols and emoji
        "^[\\s\\p{Punct}\\p{So}\\p{Sk}\\uFE0F]*" +
            // Optional "[Web발신]" prefix used by SMS gateways
            "(?:[$OPEN_BRACKETS]\\s*web\\s*발신\\s*[$CLOSE_BRACKETS]\\s*)?" +
            // The marker itself: "광고" with optional spaces or a middle dot in between
            "[$OPEN_BRACKETS]\\s*광\\s*[.·・\\u119E\\u318D]?\\s*고\\s*[$CLOSE_BRACKETS]",
        RegexOption.IGNORE_CASE
    )

    // A notification may put the channel name or a greeting on the first line
    private const val MAX_LINES_TO_CHECK = 3

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).replace(INVISIBLE_CHARS, "")

    fun isAd(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        return normalize(text.toString())
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(MAX_LINES_TO_CHECK)
            .any { AD_MARKER.containsMatchIn(it) }
    }

    /** Returns the first text that looks like an ad, or null if none does. */
    fun findAd(texts: List<CharSequence?>): CharSequence? = texts.firstOrNull { isAd(it) }
}
