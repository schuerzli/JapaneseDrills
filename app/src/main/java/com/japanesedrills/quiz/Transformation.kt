package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData.Companion.DICTIONARY

/**
 * A question type: turn the [from] conjugation into the [to] conjugation.
 *
 * [phrase] is the key of the instruction shown to the user ("negative", "past", ...),
 * [type] is the category used by the "Question focus" setting and
 * [tags] are the form options that all have to be enabled for the question to be asked.
 */
data class Transformation(
    val from: String,
    val to: String,
    val phrase: String,
    val type: String,
    val fromTags: List<String>,
    val toTags: List<String>,
    val tags: Set<String>,
    val isTrick: Boolean,
)

/**
 * Derives every transformation from the conjugation names, the same way the web drill does:
 * two conjugations are linked when their space-separated tags differ by exactly one tag.
 */
object TransformationBuilder {

    const val TRICK = "trick"

    /**
     * The [Transformation.type] a form option belongs to. Every form key is its own type
     * except "plain" and "polite", which are two ends of one question ("politeness").
     *
     * Anything mapping form options onto recorded skills has to go through this, because
     * the two vocabularies are otherwise identical and the mismatch is invisible.
     */
    fun typeOfForm(formKey: String): String =
        if (formKey == "plain" || formKey == "polite") "politeness" else formKey

    private val fromExtra = mapOf(
        "negative" to "affirmative",
        "past" to "present",
        "polite" to "plain",
        "te-form" to "non-て",
        "potential" to "non-potential",
        "conditional" to "non-conditional",
        "provisional" to "non-provisional",
        "imperative" to "non-imperative",
        "causative" to "non-causative",
        "passive" to "active",
        "progressive" to "non-progressive",
        "desire" to "'non-desire'",
        "volitional" to "non-volitional",
    )

    private val toExtra = mapOf(
        "negative" to "negative",
        "past" to "past",
        "polite" to "polite",
        "te-form" to "て",
        "potential" to "potential",
        "conditional" to "conditional",
        "provisional" to "provisional",
        "imperative" to "imperative",
        "causative" to "causative",
        "passive" to "passive",
        "progressive" to "progressive",
        "desire" to "'desire'",
        "volitional" to "volitional",
    )

    fun build(conjugationKeys: Collection<String>): List<Transformation> {
        // The dictionary form is the empty tag list.
        val allTags = LinkedHashMap<String, List<String>>()
        for (key in conjugationKeys) {
            val name = if (key == DICTIONARY) "" else key
            allTags[name] = name.split(" ")
        }

        val pairs = LinkedHashSet<Pair<String, String>>()
        for ((src, parts) in allTags) {
            if (src.isEmpty()) continue
            for (i in parts.indices) {
                val dst = parts.filterIndexed { j, _ -> j != i }.joinToString(" ")
                if (dst in allTags) {
                    val to = dst.ifEmpty { DICTIONARY }
                    pairs += src to to
                    pairs += to to src
                }
            }
        }

        val regular = pairs.mapNotNull { (from, to) -> create(from, to) }
        val tricks = regular.map { t ->
            Transformation(
                from = t.to,
                to = t.to,
                phrase = t.phrase,
                type = t.type,
                fromTags = t.toTags,
                toTags = t.toTags,
                tags = t.tags + TRICK,
                isTrick = true,
            )
        }
        return (regular + tricks).distinct()
    }

    private fun create(from: String, to: String): Transformation? {
        val fromParts = from.split(" ")
        val toParts = to.split(" ")

        val phrase = fromExtra[(fromParts - toParts.toSet()).firstOrNull()]
            ?: toExtra[(toParts - fromParts.toSet()).firstOrNull()]
            ?: return null

        val fromTags = formTags(from)
        val toTags = formTags(to)
        val type = typeOfForm(
            (fromTags - toTags.toSet()).firstOrNull()
                ?: (toTags - fromTags.toSet()).firstOrNull()
                ?: return null
        )

        return Transformation(
            from = from,
            to = to,
            phrase = phrase,
            type = type,
            fromTags = fromTags,
            toTags = toTags,
            tags = (fromTags + toTags).toSet(),
            isTrick = false,
        )
    }

    /** Tags as shown to the user: "plain" is implied whenever "polite" is absent. */
    private fun formTags(name: String): List<String> {
        val tags = name.split(" ").toMutableList()
        if ("polite" !in tags) tags.add(0, "plain")
        tags.remove(DICTIONARY)
        return tags
    }
}
