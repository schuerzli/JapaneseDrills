package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData.Companion.DICTIONARY
import com.japanesedrills.data.Word

/**
 * One step of a solution: apply [rule] to [from] to get [to]. Both are every accepted form
 * at that point, so a form with two spellings is carried through each step as two, 便利では
 * ない and 便利じゃない each becoming their own past. [shape] is what the step does to the
 * last kana, for marking the change the way the Conjugation Intro marks its examples.
 * [soundChange] is the column of the godan sound changes ([Explanations.GODAN_FUSIONS]) the
 * step applies, when it applies one.
 */
data class SolutionStep(
    val label: String,
    val rule: List<RichPart>,
    val from: List<String>,
    val to: List<String>,
    val shape: ChangeShape = ChangeShape.None,
    val soundChange: SoundChange? = null,
)

/** A column of the godan sound changes: the て-form endings or the past ones. */
enum class SoundChange { TE_FORM, PAST }

/**
 * How a target form is built from the dictionary form. [steps] is empty when the target
 * is the dictionary form itself. [usedFallback] is true when no grammar text matched.
 */
data class Solution(val steps: List<SolutionStep>, val usedFallback: Boolean = false)

/**
 * Builds the "Solution" part of an explanation from the dictionary form.
 *
 * Every form is built up from the dictionary form one form at a time: first the derivations
 * that produce a new verb or adjective (causative, passive, potential, progressive, desire),
 * then the inflections, a compound one through the simpler forms it is made of — the past
 * negative through the negative, the polite past negative through the polite and polite
 * negative. Only the first inflection depends on the word's class; after it the form ends
 * in ない, ます or です, which conjugate the same whatever the word was. Every form along
 * the way comes from the conjugation data, so the steps always agree with the accepted
 * answers; only the rule text is written here.
 */
object Explanations {

    private enum class WordClass { GODAN, ICHIDAN, SURU, KURU, IKU, ARU, IRU, I_ADJ, II, NA_ADJ }

    /** What a word conjugates as once its class stops mattering: which path an inflection takes. */
    private enum class Kind { VERB, I_ADJ, NA_ADJ }

    /** The inflections a word's class has its own rule for; the rest are built from these. */
    private enum class Op { NEG, PAST, POLITE, TE, PROV, IMP, IMP_NEG, VOL }

    private val derivationTags = setOf("causative", "passive", "potential", "progressive", "desire")

    private val NEGATIVE = setOf("negative")
    private val PAST = setOf("past")
    private val POLITE = setOf("polite")
    private val PAST_NEGATIVE = setOf("past", "negative")
    private val POLITE_NEGATIVE = setOf("polite", "negative")
    private val POLITE_PAST = setOf("polite", "past")
    private val POLITE_PAST_NEGATIVE = setOf("polite", "past", "negative")
    private val POLITE_VOLITIONAL = setOf("polite", "volitional")
    private val TE_NEGATIVE = setOf("te-form", "negative")
    private val PROVISIONAL_NEGATIVE = setOf("provisional", "negative")
    private val CONDITIONAL = setOf("conditional")
    private val CONDITIONAL_NEGATIVE = setOf("conditional", "negative")

    private val ops = mapOf(
        NEGATIVE to Op.NEG,
        PAST to Op.PAST,
        POLITE to Op.POLITE,
        setOf("te-form") to Op.TE,
        setOf("provisional") to Op.PROV,
        setOf("imperative") to Op.IMP,
        setOf("imperative", "negative") to Op.IMP_NEG,
        setOf("volitional") to Op.VOL,
    )

    /**
     * The inflections [tags] is built through, first to last. Verbs make their polite forms
     * from ます; adjectives add です to the plain form instead, except that a な-adjective's
     * です is itself the polite form and has its own past, でした.
     */
    private fun pathOf(kind: Kind, tags: Set<String>): List<Set<String>> = when (tags) {
        PAST_NEGATIVE, TE_NEGATIVE, PROVISIONAL_NEGATIVE -> listOf(NEGATIVE, tags)
        CONDITIONAL -> listOf(PAST, tags)
        CONDITIONAL_NEGATIVE -> listOf(NEGATIVE, PAST_NEGATIVE, tags)
        POLITE_VOLITIONAL -> listOf(POLITE, tags)
        POLITE_NEGATIVE -> if (kind == Kind.VERB) listOf(POLITE, tags) else listOf(NEGATIVE, tags)
        POLITE_PAST -> if (kind == Kind.I_ADJ) listOf(PAST, tags) else listOf(POLITE, tags)
        POLITE_PAST_NEGATIVE ->
            if (kind == Kind.VERB) listOf(POLITE, POLITE_NEGATIVE, tags) else listOf(NEGATIVE, PAST_NEGATIVE, tags)
        else -> listOf(tags)
    }

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

    /**
     * The sound change a step from a word of [cls] applies, if any. 行く is left out: its
     * て-form and past are the one exception to the table, labelled irregular instead.
     */
    private fun soundChangeOf(cls: WordClass, op: Op?): SoundChange? {
        if (cls != WordClass.GODAN && !(cls == WordClass.ARU && op != null && aruRule(op) == null)) return null
        return when (op) {
            Op.TE -> SoundChange.TE_FORM
            Op.PAST -> SoundChange.PAST
            else -> null
        }
    }

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
            // A form missing from rules.json would leave the chain with nothing to build on.
            val from = word.forms(previous).ifEmpty { return Solution(steps, usedFallback = true) }
            val rule = derivationRule(word, previousClass, previous, key)
            if (rule == null) fallback = true
            steps += SolutionStep(
                label = key.replaceFirstChar(Char::uppercase),
                rule = rule ?: FALLBACK,
                from = from,
                to = if (key == chain.last() && finalTags.isEmpty()) word.forms(target) else word.forms(key),
                shape = if (rule == null) ChangeShape.None else derivationShape(previousClass, key),
                // The progressive is built on the て-form, sound change and all.
                soundChange = if (key == "progressive") soundChangeOf(previousClass, Op.TE) else null,
            )
            previous = key
            previousClass = if (key == "desire") WordClass.I_ADJ else WordClass.ICHIDAN
        }
        if (finalTags.isEmpty()) return Solution(steps, fallback)

        val kind = kindOf(previousClass)
        val derived = tags - finalTags
        var built: Set<String>? = null
        for (stepTags in pathOf(kind, finalTags)) {
            // rules.json names a derived form by its tags in no fixed order: "polite passive
            // past negative", but "desire polite past".
            val key = if (stepTags == finalTags) target else keyOf(word, derived + stepTags)
            val from = word.forms(previous)
            val to = key?.let { word.forms(it) }.orEmpty()
            if (from.isEmpty() || to.isEmpty()) return Solution(steps, usedFallback = true)

            var rule: List<RichPart>?
            // After the first inflection a step always swaps or adds an ending, like 一段 verbs.
            var shape: ChangeShape = ChangeShape.Drop
            if (built == null) {
                val op = ops[stepTags]
                rule = op?.let { finalRule(previousClass, it, from.first()) }
                if (op != null) shape = finalShape(previousClass, op)
                if (rule != null && previous != DICTIONARY) {
                    rule = derivedClassNote(previous, from.first()) + lowercaseStart(rule)
                    if (previous == "potential" && from.size > 1) {
                        rule = rule + RichPart.Text(" The casual れる form takes the same ending.")
                    }
                }
            } else {
                rule = continuationRule(kind, built, stepTags, alternatives = to.size > from.size)
            }
            if (rule == null) fallback = true
            steps += SolutionStep(
                label = orderedFinalLabel(stepTags),
                rule = rule ?: FALLBACK,
                from = from,
                to = to,
                shape = if (rule == null) ChangeShape.None else shape,
                soundChange = if (built == null) soundChangeOf(previousClass, ops[stepTags]) else null,
            )
            previous = key!!
            built = stepTags
        }
        return Solution(steps, fallback)
    }

    private fun keyOf(word: Word, tags: Set<String>): String? =
        word.conjugations.keys.firstOrNull { it.split(" ").toSet() == tags }

    private fun kindOf(cls: WordClass) = when (cls) {
        WordClass.I_ADJ, WordClass.II -> Kind.I_ADJ
        WordClass.NA_ADJ -> Kind.NA_ADJ
        else -> Kind.VERB
    }

    private const val NAI = "The negative ends in ない, which conjugates like an い-adjective: "

    /**
     * An inflection built on another: the rule for what [built] became, which no longer
     * depends on the word's class. [alternatives]: whether the step adds a second, more
     * formal spelling, as くありません does beside くないです.
     */
    private fun continuationRule(kind: Kind, built: Set<String>, tags: Set<String>, alternatives: Boolean): List<RichPart>? {
        val verb = kind == Kind.VERB
        val text = when {
            built == NEGATIVE && tags == PAST_NEGATIVE -> NAI + "replace the last い with かった."
            built == NEGATIVE && tags == PROVISIONAL_NEGATIVE -> NAI + "replace the last い with ければ."
            built == NEGATIVE && tags == TE_NEGATIVE ->
                NAI + "replace the last い with くて." + if (verb) " A verb can also keep ない and add で." else ""
            tags == CONDITIONAL || tags == CONDITIONAL_NEGATIVE -> "Add ら to the past."
            verb && built == POLITE && tags == POLITE_NEGATIVE -> "Replace ます with ません."
            verb && built == POLITE && tags == POLITE_PAST -> "Replace ます with ました."
            verb && built == POLITE && tags == POLITE_VOLITIONAL -> "Replace ます with ましょう."
            verb && built == POLITE_NEGATIVE && tags == POLITE_PAST_NEGATIVE -> "Add でした."
            !verb && built == NEGATIVE && tags == POLITE_NEGATIVE ->
                if (alternatives) "Add です, or replace ない with ありません (more formal)." else "Add です."
            !verb && built == PAST_NEGATIVE && tags == POLITE_PAST_NEGATIVE ->
                if (alternatives) "Add です, or replace なかった with ありませんでした (more formal)." else "Add です."
            kind == Kind.I_ADJ && built == PAST && tags == POLITE_PAST -> "Add です."
            kind == Kind.NA_ADJ && built == POLITE && tags == POLITE_PAST -> "Replace です with でした."
            else -> return null
        }
        return rule(text)
    }

    private val FALLBACK = listOf(RichPart.Text("Change the ending as shown."))

    /**
     * The whole rule for an irregular change: a label, not an explanation. There is nothing
     * to derive, only a form to learn, and the change shown under it is that form. する and
     * 来る are irregular throughout; 行く and ある are godan verbs irregular in a few forms.
     */
    private val IRREGULAR = listOf(RichPart.Tag("Irregular"))
    private val IRREGULAR_HERE = listOf(RichPart.Tag("Irregular in this case"))

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
                    rule("Change the last kana from the う-row to the あ-row and add せる.$wa")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                WordClass.ARU -> rule("Change the last る to ら and add せる. This form of ある is rare.")
                WordClass.ICHIDAN, WordClass.IRU -> rule("Drop the last る and add させる.")
                WordClass.SURU, WordClass.KURU -> IRREGULAR
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
                    rule("Change the last kana from the う-row to the あ-row and add れる.$wa")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                WordClass.ARU -> rule("Change the last る to ら and add れる. This form of ある is rare.")
                WordClass.ICHIDAN, WordClass.IRU ->
                    rule("Drop the last る and add られる. (The potential form looks the same.)")
                WordClass.SURU, WordClass.KURU -> IRREGULAR
                else -> null
            }
            "potential" -> when (cls) {
                WordClass.GODAN, WordClass.IKU ->
                    rule("Change the last kana from the う-row to the え-row and add る.")
                // Only reachable if the disabled ある forms are re-enabled in rules.json.
                // ある has no common potential form; あり得る is used instead.
                WordClass.ARU -> rule("Change the last る to れ and add る. ある is hardly ever used this way.")
                WordClass.ICHIDAN, WordClass.IRU ->
                    rule("Drop the last る and add られる. In casual speech just れる is common too.")
                WordClass.SURU, WordClass.KURU -> IRREGULAR
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

    /**
     * What a derivation does to the last kana. The causative passive is left unmarked: its
     * shorter godan form (書かされる) and its full one do not share a split to mark.
     */
    private fun derivationShape(cls: WordClass, key: String): ChangeShape {
        val godan = cls == WordClass.GODAN || cls == WordClass.IKU || cls == WordClass.ARU
        val ichidan = cls == WordClass.ICHIDAN || cls == WordClass.IRU
        return when {
            key == "progressive" && (cls == WordClass.GODAN || cls == WordClass.IKU) -> ChangeShape.Fuse(tail = "いる")
            key == "causative passive" -> ChangeShape.None
            godan && key in derivationTags -> ChangeShape.Shift
            ichidan -> ChangeShape.Drop
            else -> ChangeShape.None
        }
    }

    /** What a final inflection does to the last kana; irregular changes mark nothing. */
    private fun finalShape(cls: WordClass, op: Op): ChangeShape = when (cls) {
        WordClass.GODAN, WordClass.IKU, WordClass.ARU -> when {
            cls == WordClass.ARU && aruRule(op) != null -> ChangeShape.None
            op == Op.TE || op == Op.PAST -> ChangeShape.Fuse()
            else -> ChangeShape.Shift
        }
        WordClass.ICHIDAN, WordClass.IRU, WordClass.I_ADJ, WordClass.NA_ADJ -> ChangeShape.Drop
        WordClass.SURU, WordClass.KURU, WordClass.II -> ChangeShape.None
    }

    // Final inflections.

    private fun finalRule(cls: WordClass, op: Op, base: String): List<RichPart>? = when (cls) {
        WordClass.GODAN -> godanRule(op, lastKana(base))
        WordClass.IKU -> ikuRule(op) ?: godanRule(op, 'く')
        WordClass.ARU -> aruRule(op) ?: godanRule(op, 'る')
        WordClass.ICHIDAN -> ichidanRule(op)
        WordClass.IRU -> ichidanRule(op)?.let {
            if (op == Op.IMP) rule("Drop the last る and add ろ (or よ in writing).") else it
        }
        WordClass.SURU -> suruRule(op)
        WordClass.KURU -> kuruRule(op)
        WordClass.I_ADJ -> iAdjectiveRule(op)
        WordClass.II -> iiRule(op)
        WordClass.NA_ADJ -> naAdjectiveRule(op)
    }

    /**
     * A row shift is stated as the rule, not as the one substitution this word happens to
     * need: "the last kana" rather than "the last く". Naming the kana made the reference
     * read as though く were the only ending a godan verb has, and the worked example
     * underneath already shows what the shift does to this particular word.
     *
     * The て and past endings stay concrete, because there the ending genuinely differs by
     * kana — that is a table to learn, not an instance of a pattern.
     */
    private fun godanRule(op: Op, u: Char): List<RichPart>? {
        val te = godanTe[u] ?: return null
        val ta = pastOf(te)
        val wa = if (u == 'う') " う becomes わ, not あ." else ""
        return when (op) {
            Op.NEG -> rule("Change the last kana from the う-row to the あ-row and add ない.$wa")
            Op.POLITE -> rule("Change the last kana from the う-row to the い-row and add ます.")
            Op.TE -> rule("Godan verbs ending in $u replace it with $te.")
            Op.PAST -> rule("Godan verbs ending in $u replace it with $ta (the same sound change as the て-form $te).")
            Op.PROV -> rule("Change the last kana from the う-row to the え-row and add ば.")
            Op.IMP -> rule("Change the last kana from the う-row to the え-row.")
            Op.IMP_NEG -> rule("Add な to the dictionary form.")
            Op.VOL -> rule("Change the last kana from the う-row to the お-row and add う (a long お sound).")
        }
    }

    private fun ikuRule(op: Op): List<RichPart>? = when (op) {
        Op.TE, Op.PAST -> IRREGULAR_HERE
        else -> null
    }

    private fun aruRule(op: Op): List<RichPart>? = when (op) {
        Op.NEG -> IRREGULAR_HERE
        else -> null
    }

    private fun ichidanRule(op: Op): List<RichPart> = when (op) {
        Op.NEG -> rule("Drop the last る and add ない.")
        Op.POLITE -> rule("Drop the last る and add ます.")
        Op.PAST -> rule("Drop the last る and add た.")
        Op.TE -> rule("Drop the last る and add て.")
        Op.PROV -> rule("Change the last る to れ and add ば.")
        Op.IMP -> rule("Drop the last る and add ろ.")
        Op.IMP_NEG -> rule("Add な to the dictionary form.")
        Op.VOL -> rule("Drop the last る and add よう.")
    }

    private fun suruRule(op: Op): List<RichPart> = when (op) {
        Op.IMP_NEG -> rule("Add な to the dictionary form: するな.")
        else -> IRREGULAR
    }

    private fun kuruRule(op: Op): List<RichPart> = when (op) {
        Op.IMP_NEG -> rule("Add な to the dictionary form: ", jp("来[く]るな"), ".")
        else -> IRREGULAR
    }

    private fun iAdjectiveRule(op: Op): List<RichPart>? = when (op) {
        Op.NEG -> rule("Replace the last い with く and add ない.")
        Op.PAST -> rule("Replace the last い with かった.")
        Op.POLITE -> rule("Add です.")
        Op.TE -> rule("Replace the last い with くて.")
        else -> null
    }

    private fun iiRule(op: Op): List<RichPart>? {
        if (op == Op.POLITE) return rule("Add です: いいです.")
        val base = iAdjectiveRule(op) ?: return null
        return rule("いい conjugates from its older form ", jp("良[よ]い"), ". ") + base
    }

    private fun naAdjectiveRule(op: Op): List<RichPart>? {
        val intro = rule("な-adjectives are listed here with だ. ")
        val body = when (op) {
            Op.NEG -> rule("Replace だ with ではない, or with the more casual じゃない.")
            Op.PAST -> rule("Replace だ with だった.")
            Op.POLITE -> rule("Replace だ with です.")
            Op.TE -> rule("Replace だ with で.")
            else -> return null
        }
        return intro + body
    }
}
