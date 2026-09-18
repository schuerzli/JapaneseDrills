package com.japanesedrills.quiz

/**
 * Converts typed romaji to hiragana as the user types, using the web drill's tables.
 * Incomplete romaji (e.g. a lone "k") is left in place until the next key completes it.
 */
object RomajiConverter {

    private val one = mapOf(
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
    )

    private val two: Map<String, String> = buildMap {
        putAll(
            mapOf(
                "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
                "sa" to "さ", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
                "ta" to "た", "ti" to "ち", "tu" to "つ", "te" to "て", "to" to "と",
                "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
                "ha" to "は", "hi" to "ひ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
                "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
                "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
                "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
                "za" to "ざ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
                "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
                "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
                "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
                "qa" to "くぁ", "qi" to "くぃ", "qu" to "く", "qe" to "くぇ", "qo" to "くぉ",
                "wa" to "わ", "wi" to "うぃ", "wu" to "う", "we" to "うぇ", "wo" to "を",
                "ya" to "や", "yi" to "い", "yu" to "ゆ", "ye" to "いぇ", "yo" to "よ",
                "fa" to "ふぁ", "fi" to "ふぃ", "fu" to "ふ", "fe" to "ふぇ", "fo" to "ふぉ",
                "ja" to "じゃ", "ji" to "じ", "ju" to "じゅ", "je" to "じぇ", "jo" to "じょ",
                "la" to "ぁ", "li" to "ぃ", "lu" to "ぅ", "le" to "ぇ", "lo" to "ぉ",
                "xa" to "ぁ", "xi" to "ぃ", "xu" to "ぅ", "xe" to "ぇ", "xo" to "ぉ",
                "ca" to "か", "ci" to "し", "cu" to "く", "ce" to "せ", "co" to "こ",
                "va" to "ヴぁ", "vi" to "ヴぃ", "vu" to "ヴ", "ve" to "ヴぇ", "vo" to "ヴぉ",
                "nn" to "ん", "n'" to "ん",
            )
        )
        // "n" before a consonant is ん; the consonant stays for the next syllable.
        "bcdfghjklmpqrstvwxz".forEach { put("n$it", "ん$it") }
        // A doubled consonant is a small っ followed by that consonant.
        "bcdfghjklmpqrstvwxyz".forEach { put("$it$it", "っ$it") }
    }

    private val three = buildMap {
        val rows = mapOf(
            "ky" to "き", "ny" to "に", "hy" to "ひ", "my" to "み", "ry" to "り",
            "gy" to "ぎ", "zy" to "じ", "dy" to "ぢ", "by" to "び", "py" to "ぴ",
        )
        val small = listOf("a" to "ゃ", "i" to "ぃ", "u" to "ゅ", "e" to "ぇ", "o" to "ょ")
        for ((prefix, kana) in rows) {
            for ((vowel, smallKana) in small) put(prefix + vowel, kana + smallKana)
        }
        for ((prefix, kana) in listOf("sh" to "し", "ch" to "ち")) {
            for ((vowel, smallKana) in small) {
                put(prefix + vowel, if (vowel == "i") kana else kana + smallKana)
            }
        }
        put("jiu", "じゅう")
        put("jyu", "じゅ")
        put("jyo", "じょ")
        put("tsu", "つ")
        // The explicit small っ, as typed on a standard IME.
        put("ltu", "っ")
        put("xtu", "っ")
    }

    private val tables = listOf(3 to three, 2 to two, 1 to one)

    fun convert(input: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            var matched = false
            for ((len, table) in tables) {
                if (i + len > input.length) continue
                val replacement = table[input.substring(i, i + len).lowercase()] ?: continue
                val last = replacement.last()
                if (last in 'a'..'z') {
                    // Keep the trailing latin letter so it can start the next syllable.
                    out.append(replacement, 0, replacement.length - 1)
                    i += len - 1
                } else {
                    out.append(replacement)
                    i += len
                }
                matched = true
                break
            }
            if (!matched) {
                out.append(input[i])
                i++
            }
        }
        return out.toString()
    }

    /** Conversion applied on submit: a trailing "n" can only mean ん at that point. */
    fun finish(input: String): String {
        val converted = convert(input)
        return if (converted.endsWith("n") || converted.endsWith("N")) converted.dropLast(1) + "ん" else converted
    }
}
