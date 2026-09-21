package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData.Companion.DICTIONARY
import com.japanesedrills.data.Word

/** One step of a solution: apply [rule] to [from] to get [to] (one or more accepted forms). */
data class SolutionStep(
    val label: String,
    val rule: List<RichPart>,
    val from: String,
    val to: List<String>,
)

/**
 * How a target form is built from the dictionary form. [steps] is empty when the target
 * is the dictionary form itself. [usedFallback] is true when no grammar text matched.
 */
data class Solution(val steps: List<SolutionStep>, val usedFallback: Boolean = false)

/**
 * Builds the "Solution" part of an explanation from the dictionary form.
 *
 * A form is split into derivation steps that produce a new verb or adjective
 * (causative, passive, potential, progressive, desire) and one final inflection
 * (negative, polite, past, …). Intermediate forms come from the conjugation data,
 * so the steps always agree with the accepted answers; only the rule text is written here.
 */
object Explanations {

    private enum class WordClass { GODAN, ICHIDAN, SURU, KURU, IKU, ARU, IRU, I_ADJ, II, NA_ADJ }

    private enum class Op {
        NEG, PAST, PAST_NEG, POLITE, POLITE_NEG, POLITE_PAST, POLITE_PAST_NEG, POLITE_VOL,
        TE, TE_NEG, COND, COND_NEG, PROV, PROV_NEG, IMP, IMP_NEG, VOL,
    }

    private val derivationTags = setOf("causative", "passive", "potential", "progressive", "desire")

    private val ops = mapOf(
        setOf("negative") to Op.NEG,
        setOf("past") to Op.PAST,
        setOf("past", "negative") to Op.PAST_NEG,
        setOf("polite") to Op.POLITE,
        setOf("polite", "negative") to Op.POLITE_NEG,
        setOf("polite", "past") to Op.POLITE_PAST,
        setOf("polite", "past", "negative") to Op.POLITE_PAST_NEG,
        setOf("polite", "volitional") to Op.POLITE_VOL,
        setOf("te-form") to Op.TE,
        setOf("te-form", "negative") to Op.TE_NEG,
        setOf("conditional") to Op.COND,
        setOf("conditional", "negative") to Op.COND_NEG,
        setOf("provisional") to Op.PROV,
        setOf("provisional", "negative") to Op.PROV_NEG,
        setOf("imperative") to Op.IMP,
        setOf("imperative", "negative") to Op.IMP_NEG,
        setOf("volitional") to Op.VOL,
    )

    // Kana rows used by godan verbs: あ, い, え and お rows for each dictionary ending.
    // The て-form ending that replaces each godan dictionary ending, in the order the
    // fusions are usually taught. Its keys double as the set of kana a godan verb can end
    // in: the row shifts are described in words, so the rows need no table of their own.
    private val godanTe = mapOf(
        'う' to "って", 'つ' to "って", 'る' to "って", 'ぬ' to "んで", 'ぶ' to "んで",
        'む' to "んで", 'く' to "いて", 'ぐ' to "いで", 'す' to "して",
    )

    /** The past ending for a て ending: the same fusion with a different tail. */
    private fun pastOf(te: String) = te.dropLast(1) + if (te.last() == 'て') "た" else "だ"

    /** One row of the godan sound changes: the endings that fuse the same way. */
    data class Fusion(val endings: List<Char>, val te: String, val past: String)

    /**
     * The godan sound changes as a closed table, grouped by the ending they share.
     *
     * This is the one part of a conjugation that no rule can summarise — it has to be
     * learned as a list — so the reference shows the list rather than a rule per example
     * word. It is built from the same map the explanations are written from, so the table
     * cannot show a change the drill would mark wrong.
     */
    val GODAN_FUSIONS: List<Fusion> = godanTe.entries
        .groupBy({ it.value }, { it.key })
        .map { (te, endings) -> Fusion(endings, te, pastOf(te)) }

    /** The fusion table when [target] is built with one, or null when a row shift builds it. */
    fun godanFusions(target: String): List<Fusion>? =
        GODAN_FUSIONS.takeIf { target == "te-form" || target == "past" }

    fun solution(word: Word, target: String): Solution {
        if (target == DICTIONARY) return Solution(emptyList())

        val tags = target.split(" ").toSet()
        val chain = buildList {
            if ("causative" in tags) {
                add("causative")
                if ("passive" in tags) add("causative passive")
            } else if ("passive" in tags) {
                add("passive")
            }
            if ("potential" in tags) add("potential")
            if ("progressive" in tags) add("progressive")
            if ("desire" in tags) add("desire")
        }
        val finalTags = tags - derivationTags

        val steps = ArrayList<SolutionStep>()
        var fallback = false
        var previous = DICTIONARY
        var previousClass = classOf(word.group)

        for (key in chain) {
            val forms = word.forms(key)
            // A form missing from rules.json would leave the chain with nothing to build on.
            val from = word.forms(previous).firstOrNull() ?: return Solution(steps, usedFallback = true)
            val rule = derivationRule(word, previousClass, previous, key)
            if (rule == null) fallback = true
            steps += SolutionStep(
                label = key.replaceFirstChar(Char::uppercase),
                rule = rule ?: FALLBACK,
                from = from,
                to = if (key == chain.last() && finalTags.isEmpty()) word.forms(target) else forms,
            )
            previous = key
            previousClass = if (key == "desire") WordClass.I_ADJ else WordClass.ICHIDAN
        }

        if (finalTags.isNotEmpty()) {
            val op = ops[finalTags]
            val base = word.forms(previous).firstOrNull() ?: return Solution(steps, usedFallback = true)
            val answers = word.forms(target)
            var rule = op?.let { finalRule(previousClass, it, base, alternatives = answers.size > 1) }
            if (rule == null) {
                fallback = true
                rule = FALLBACK
            } else if (previous != DICTIONARY) {
                rule = derivedClassNote(previous, base) + lowercaseStart(rule)
                if (previous == "potential" && answers.size > 1) {
                    rule = rule + RichPart.Text(" The casual れる form takes the same ending.")
                }
            }
            steps += SolutionStep(
                label = orderedFinalLabel(finalTags),
                rule = rule,
                from = base,
                to = answers,
            )
        }

        return Solution(steps, fallback)
    }

    private val FALLBACK = listOf(RichPart.Text("Change the ending as shown."))

    private fun Word.forms(key: String): List<String> = conjugations[key]?.forms.orEmpty()

    private fun classOf(group: String) = when (group) {
        "godan" -> WordClass.GODAN
        "ichidan" -> WordClass.ICHIDAN
        "suru" -> WordClass.SURU
        "kuru" -> WordClass.KURU
        "iku" -> WordClass.IKU
        "aru" -> WordClass.ARU
        "iru" -> WordClass.IRU
        "i-adjective" -> WordClass.I_ADJ
        "ii" -> WordClass.II
        else -> WordClass.NA_ADJ
    }

    private val finalOrder = listOf("polite", "te-form", "conditional", "provisional", "imperative", "volitional", "past", "negative")

    private fun orderedFinalLabel(tags: Set<String>): String =
        finalOrder.filter { it in tags }
            .joinToString(" ") { if (it == "te-form") "て-form" else it }
            .replaceFirstChar(Char::uppercase)

    // Rule text helpers: plain strings become text, jp() marks a Japanese word with furigana.

    private class Jp(val word: String)

    private fun jp(word: String) = Jp(word)

    private fun rule(vararg parts: Any): List<RichPart> = parts.map {
        when (it) {
            is Jp -> RichPart.Jp(it.word)
            else -> RichPart.Text(it.toString())
        }
    }

    private fun lowercaseStart(parts: List<RichPart>): List<RichPart> {
        val first = parts.firstOrNull() as? RichPart.Text ?: return parts
        return listOf(first.copy(text = first.text.replaceFirstChar(Char::lowercase))) + parts.drop(1)
    }

    private fun lastKana(form: String): Char = Furigana.toKana(form).last()

    private fun derivedClassNote(key: String, base: String): List<RichPart> = when (key) {
        "desire" -> rule("The たい form conjugates like an い-adjective: ")
        else -> rule(jp(base), " conjugates like an ichidan verb: ")
    }

    // Derivations: steps that turn the word into a new verb or adjective.

    private fun derivationRule(word: Word, cls: WordClass, fromKey: String, key: String): List<RichPart>? {
        val from = word.forms(fromKey).firstOrNull() ?: return null
        val wa = if (lastKana(from) == 'う') " う becomes わ, not あ." else ""
        return when (key) {
            "causative" -> when (cls) {
                WordClass.GODAN, WordClass.IKU ->
                    rule("Change the final kana from the う-row to the あ-row and add せる.$wa")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                WordClass.ARU -> rule("Change the final る to ら and add せる. This form of ある is rare.")
                WordClass.ICHIDAN, WordClass.IRU -> rule("Drop the final る and add させる.")
                WordClass.SURU -> rule("する becomes させる.")
                WordClass.KURU -> rule(jp("来[く]る"), " is irregular: it becomes ", jp("来[こ]させる"), ".")
                else -> null
            }
            "causative passive" -> {
                val base = rule("The causative form is an ichidan verb. Make it passive by dropping る and adding られる.")
                // Godan verbs (except those ending in す) also have a contracted form: 書かされる.
                val dictionaryEnd = lastKana(word.dictionary)
                val original = classOf(word.group)
                if ((original == WordClass.GODAN || original == WordClass.IKU) && dictionaryEnd != 'す') {
                    base + rule(" Godan verbs also have a shorter form: add される to the あ-row kana instead.")
                } else {
                    base
                }
            }
            "passive" -> when (cls) {
                WordClass.GODAN, WordClass.IKU ->
                    rule("Change the final kana from the う-row to the あ-row and add れる.$wa")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                WordClass.ARU -> rule("Change the final る to ら and add れる. This form of ある is rare.")
                WordClass.ICHIDAN, WordClass.IRU ->
                    rule("Drop the final る and add られる. (The potential form looks the same.)")
                WordClass.SURU -> rule("する becomes される.")
                WordClass.KURU -> rule(jp("来[く]る"), " is irregular: it becomes ", jp("来[こ]られる"), ".")
                else -> null
            }
            "potential" -> when (cls) {
                WordClass.GODAN, WordClass.IKU ->
                    rule("Change the final kana from the う-row to the え-row and add る.")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                // ある has no common potential form; あり得る is used instead.
                WordClass.ARU -> rule("Change the final る to れ and add る. ある is hardly ever used this way.")
                WordClass.ICHIDAN, WordClass.IRU ->
                    rule("Drop the final る and add られる. In casual speech just れる is common too.")
                WordClass.SURU -> rule("する is replaced by できる.")
                WordClass.KURU -> rule(jp("来[く]る"), " is irregular: it becomes ", jp("来[こ]られる"), ".")
                else -> null
            }
            "progressive" -> {
                val te = word.forms("te-form").firstOrNull() ?: return null
                rule("Take the て-form ", jp(te), " and add いる.")
            }
            "desire" -> {
                val stem = word.forms("polite").firstOrNull()?.removeSuffix("ます") ?: return null
                rule("Take ", jp(stem), " and add たい.")
            }
            else -> null
        }
    }

    // Final inflections.

    private fun finalRule(cls: WordClass, op: Op, base: String, alternatives: Boolean): List<RichPart>? = when (cls) {
        WordClass.GODAN -> godanRule(op, lastKana(base))
        WordClass.IKU -> ikuRule(op) ?: godanRule(op, 'く')
        WordClass.ARU -> aruRule(op) ?: godanRule(op, 'る')
        WordClass.ICHIDAN -> ichidanRule(op)
        WordClass.IRU -> ichidanRule(op)?.let {
            if (op == Op.IMP) rule("Drop the final る and add ろ (or よ in writing).") else it
        }
        WordClass.SURU -> suruRule(op)
        WordClass.KURU -> kuruRule(op)
        WordClass.I_ADJ -> iAdjectiveRule(op, alternatives)
        WordClass.II -> iiRule(op, alternatives)
        WordClass.NA_ADJ -> naAdjectiveRule(op)
    }

    private val masuEndings = mapOf(
        Op.POLITE to "ます", Op.POLITE_NEG to "ません", Op.POLITE_PAST to "ました",
        Op.POLITE_PAST_NEG to "ませんでした", Op.POLITE_VOL to "ましょう",
    )

    /**
     * A row shift is stated as the rule, not as the one substitution this word happens to
     * need: "the final kana" rather than "the final く". Naming the kana made the reference
     * read as though く were the only ending a godan verb has, and the worked example
     * underneath already shows what the shift does to this particular word.
     *
     * The て and past endings stay concrete, because there the ending genuinely differs by
     * kana — that is a table to learn, not an instance of a pattern.
     */
    private fun godanRule(op: Op, u: Char): List<RichPart>? {
        val te = godanTe[u] ?: return null
        val ta = pastOf(te)
        val aRow = "Change the final kana from the う-row to the あ-row"
        val wa = if (u == 'う') " う becomes わ, not あ." else ""
        return when (op) {
            Op.NEG -> rule("$aRow and add ない.$wa")
            Op.PAST_NEG -> rule("$aRow and add なかった.$wa")
            Op.TE_NEG -> rule("$aRow and add なくて or ないで.$wa")
            Op.COND_NEG -> rule("$aRow and add なかったら.$wa")
            Op.PROV_NEG -> rule("$aRow and add なければ.$wa")
            Op.POLITE, Op.POLITE_NEG, Op.POLITE_PAST, Op.POLITE_PAST_NEG, Op.POLITE_VOL ->
                rule(
                    "Change the final kana from the う-row to the い-row and add " +
                        "${masuEndings.getValue(op)}.",
                )
            Op.TE -> rule("Godan verbs ending in $u replace it with $te.")
            Op.PAST -> rule("Godan verbs ending in $u replace it with $ta (the same sound change as the て-form $te).")
            Op.COND -> rule("Make the past form (ending in $ta) and add ら.")
            Op.PROV -> rule("Change the final kana from the う-row to the え-row and add ば.")
            Op.IMP -> rule("Change the final kana from the う-row to the え-row.")
            Op.IMP_NEG -> rule("Add な to the dictionary form.")
            Op.VOL -> rule("Change the final kana from the う-row to the お-row and add う (a long お sound).")
        }
    }

    private fun ikuRule(op: Op): List<RichPart>? = when (op) {
        Op.TE -> rule(jp("行[い]く"), " is the one exception to the く → いて rule: its て-form is ", jp("行[い]って"), ".")
        Op.PAST -> rule(jp("行[い]く"), " is the one exception to the く → いた rule: its past is ", jp("行[い]った"), ".")
        Op.COND -> rule("Make the past form ", jp("行[い]った"), " (an exception to the く → いた rule) and add ら.")
        else -> null
    }

    private fun aruRule(op: Op): List<RichPart>? = when (op) {
        Op.NEG -> rule("ある is irregular: its negative is simply ない (never あらない).")
        Op.PAST_NEG -> rule("ある is irregular: its negative is ない, so the past negative is なかった.")
        Op.TE_NEG -> rule("ある is irregular: its negative is ない, so this is なくて or ないで.")
        Op.COND_NEG -> rule("ある is irregular: its negative is ない, so this is なかったら.")
        Op.PROV_NEG -> rule("ある is irregular: its negative is ない, so this is なければ.")
        // Only reachable if the disabled ある imperative is re-enabled in rules.json.
        Op.IMP -> rule("Change the final る to its え-row kana れ. This form of ある is rare.")
        else -> null
    }

    private fun ichidanRule(op: Op): List<RichPart>? = when (op) {
        Op.NEG -> rule("Drop the final る and add ない.")
        Op.PAST_NEG -> rule("Drop the final る and add なかった.")
        Op.POLITE, Op.POLITE_NEG, Op.POLITE_PAST, Op.POLITE_PAST_NEG, Op.POLITE_VOL ->
            rule("Drop the final る and add ${masuEndings.getValue(op)}.")
        Op.PAST -> rule("Drop the final る and add た.")
        Op.TE -> rule("Drop the final る and add て.")
        Op.TE_NEG -> rule("Drop the final る and add なくて or ないで.")
        Op.COND -> rule("Drop the final る and add たら (the past form plus ら).")
        Op.COND_NEG -> rule("Drop the final る and add なかったら.")
        Op.PROV -> rule("Change the final る to れ and add ば.")
        Op.PROV_NEG -> rule("Drop the final る and add なければ.")
        Op.IMP -> rule("Drop the final る and add ろ.")
        Op.IMP_NEG -> rule("Add な to the dictionary form.")
        Op.VOL -> rule("Drop the final る and add よう.")
    }

    private fun suruRule(op: Op): List<RichPart> = when (op) {
        Op.NEG -> rule("する becomes し, then add ない.")
        Op.PAST_NEG -> rule("する becomes し, then add なかった.")
        Op.POLITE, Op.POLITE_NEG, Op.POLITE_PAST, Op.POLITE_PAST_NEG, Op.POLITE_VOL ->
            rule("する becomes し, then add ${masuEndings.getValue(op)}.")
        Op.PAST -> rule("する becomes し, then add た.")
        Op.TE -> rule("する becomes し, then add て.")
        Op.TE_NEG -> rule("する becomes し, then add なくて or ないで.")
        Op.COND -> rule("する becomes し, then add たら.")
        Op.COND_NEG -> rule("する becomes し, then add なかったら.")
        Op.PROV -> rule("する becomes すれ, then add ば.")
        Op.PROV_NEG -> rule("する becomes し, then add なければ.")
        Op.IMP -> rule("する becomes しろ, or せよ in formal writing.")
        Op.IMP_NEG -> rule("Add な to the dictionary form: するな.")
        Op.VOL -> rule("する becomes し, then add よう.")
    }

    private fun kuruRule(op: Op): List<RichPart> {
        val ko = jp("来[こ]")
        val ki = jp("来[き]")
        val ku = jp("来[く]")
        val irregular = "来[く]る is irregular. "
        return when (op) {
            Op.NEG -> rule(irregular, "Before ない it becomes ", ko, ": add ない.")
            Op.PAST_NEG -> rule(irregular, "Before ない it becomes ", ko, ": add なかった.")
            Op.TE_NEG -> rule(irregular, "Before ない it becomes ", ko, ": add なくて or ないで.")
            Op.COND_NEG -> rule(irregular, "Before ない it becomes ", ko, ": add なかったら.")
            Op.PROV_NEG -> rule(irregular, "Before ない it becomes ", ko, ": add なければ.")
            Op.POLITE, Op.POLITE_NEG, Op.POLITE_PAST, Op.POLITE_PAST_NEG, Op.POLITE_VOL ->
                rule(irregular, "Before ${masuEndings.getValue(op)} it becomes ", ki, ": add ${masuEndings.getValue(op)}.")
            Op.PAST -> rule(irregular, "Before た it becomes ", ki, ": add た.")
            Op.TE -> rule(irregular, "Before て it becomes ", ki, ": add て.")
            Op.COND -> rule(irregular, "Before たら it becomes ", ki, ": add たら.")
            Op.PROV -> rule(irregular, "Before ば it keeps the reading ", ku, ": add れば.")
            Op.IMP -> rule(irregular, "Its imperative is ", jp("来[こ]い"), ".")
            Op.IMP_NEG -> rule("Add な to the dictionary form: ", jp("来[く]るな"), ".")
            Op.VOL -> rule(irregular, "Before よう it becomes ", ko, ": add よう.")
        }
    }

    /** [alternatives]: whether the formal ありません variants are accepted as well. */
    private fun iAdjectiveRule(op: Op, alternatives: Boolean): List<RichPart>? = when (op) {
        Op.NEG -> rule("Replace the final い with く and add ない.")
        Op.PAST_NEG -> rule("Replace the final い with く and add なかった.")
        Op.PAST -> rule("Replace the final い with かった.")
        Op.POLITE -> rule("Add です.")
        Op.POLITE_NEG -> if (alternatives) {
            rule("Replace the final い with くないです, or with くありません (more formal).")
        } else {
            rule("Replace the final い with くない and add です.")
        }
        Op.POLITE_PAST -> rule("Replace the final い with かった and add です.")
        Op.POLITE_PAST_NEG -> if (alternatives) {
            rule("Replace the final い with くなかったです, or with くありませんでした (more formal).")
        } else {
            rule("Replace the final い with くなかった and add です.")
        }
        Op.TE -> rule("Replace the final い with くて.")
        Op.TE_NEG -> rule("Replace the final い with く and add なくて.")
        else -> null
    }

    private fun iiRule(op: Op, alternatives: Boolean): List<RichPart>? {
        if (op == Op.POLITE) return rule("Add です: いいです.")
        val base = iAdjectiveRule(op, alternatives) ?: return null
        return rule("いい conjugates from its older form ", jp("良[よ]い"), ". ") + base
    }

    private fun naAdjectiveRule(op: Op): List<RichPart>? {
        val intro = rule("な-adjectives are listed here with だ. ")
        val body = when (op) {
            Op.NEG -> rule("Replace だ with ではない, or with the more casual じゃない.")
            Op.PAST_NEG -> rule("Replace だ with ではなかった, or with the more casual じゃなかった.")
            Op.PAST -> rule("Replace だ with だった.")
            Op.POLITE -> rule("Replace だ with です.")
            Op.POLITE_NEG ->
                rule("Replace だ with ではありません or じゃありません, or with ではないです or じゃないです.")
            Op.TE -> rule("Replace だ with で.")
            Op.TE_NEG -> rule("Replace だ with ではなくて, or with the more casual じゃなくて.")
            Op.POLITE_PAST -> rule("Replace だ with でした.")
            Op.POLITE_PAST_NEG ->
                rule(
                    "Replace だ with ではありませんでした or じゃありませんでした, " +
                        "or with ではなかったです or じゃなかったです.",
                )
            else -> return null
        }
        return intro + body
    }
}
