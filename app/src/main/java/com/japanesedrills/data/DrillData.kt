package com.japanesedrills.data

import android.content.Context
import com.japanesedrills.quiz.Curriculum
import com.japanesedrills.quiz.Transformation
import com.japanesedrills.quiz.TransformationBuilder
import org.json.JSONObject

/** One suffix-replacement rule (`before` -> `after`) or a fixed irregular `result`. */
data class Rule(val before: String?, val after: String?, val result: String?)

data class RuleSet(val forms: List<Rule>, val tetakei: Boolean)

/** Forms are stored in furigana notation, e.g. `食[た]べない`. */
data class Conjugation(val forms: List<String>, val tetakei: Boolean)

data class Word(
    val key: String,
    val group: String,
    val dictionary: String,
    val meaning: String,
    val sentenceJp: String,
    val sentenceEn: String,
    val tags: Set<String>,
    val conjugations: Map<String, Conjugation>,
)

/** Word list with all conjugations pre-computed, plus every possible question transformation. */
class DrillData(
    val words: List<Word>,
    val transformations: List<Transformation>,
    val curriculum: Curriculum,
    /** Per group, the conjugations it defines itself. See [Companion.parseOwnForms]. */
    val ownForms: Map<String, Set<String>>,
) {

    /** Words by key, for the learn path, which names its vocabulary rather than filtering it. */
    val wordsByKey: Map<String, Word> = words.associateBy { it.key }

    companion object {
        const val DICTIONARY = "dictionary"

        fun load(context: Context): DrillData {
            fun asset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(asset("words.json"), asset("rules.json"), asset("lessons.json"))
        }

        fun fromJson(wordsJson: String, rulesJson: String, lessonsJson: String): DrillData {
            val rules = parseRules(rulesJson)
            val words = parseWords(wordsJson, rules)
            val conjugationKeys = LinkedHashSet<String>().apply {
                add(DICTIONARY)
                rules.values.forEach { addAll(it.keys) }
            }
            return DrillData(
                words,
                TransformationBuilder.build(conjugationKeys),
                Curriculum.parse(lessonsJson),
                parseOwnForms(rulesJson),
            )
        }

        /**
         * A group may declare `"_extends": "<group>"` to inherit that group's forms and list
         * only the ones it conjugates differently, so e.g. 行く does not restate all of godan.
         * Groups that deliberately lack forms (ある, いる) stay standalone rather than
         * inheriting a set they would have to subtract from again.
         */
        /**
         * The conjugations each group declares itself, as opposed to inheriting through
         * `_extends`. This is the difference between "irregular verb" and "irregular here":
         * 行く extends godan and overrides only the forms built on its て-form, so it is a
         * perfectly ordinary godan verb everywhere else.
         */
        fun parseOwnForms(json: String): Map<String, Set<String>> {
            val root = JSONObject(json)
            return root.keys().asSequence().associateWith { group ->
                val groupObj = root.getJSONObject(group)
                groupObj.keys().asSequence().filterNot { it.startsWith("_") }.toSet()
            }
        }

        fun parseRules(json: String): Map<String, Map<String, RuleSet>> {
            val root = JSONObject(json)
            val result = LinkedHashMap<String, Map<String, RuleSet>>()

            fun resolve(group: String, seen: Set<String>): Map<String, RuleSet> {
                result[group]?.let { return it }
                check(group !in seen) { "Cyclic _extends through $group" }
                val groupObj = root.getJSONObject(group)
                val sets = LinkedHashMap<String, RuleSet>()
                groupObj.optStringOrNull("_extends")?.let { sets.putAll(resolve(it, seen + group)) }
                for (conjugation in groupObj.keys()) {
                    // Keys starting with "_" are directives, notes or disabled forms.
                    if (conjugation.startsWith("_")) continue
                    val obj = groupObj.getJSONObject(conjugation)
                    val formsArr = obj.getJSONArray("forms")
                    val forms = (0 until formsArr.length()).map { i ->
                        val r = formsArr.getJSONObject(i)
                        Rule(r.optStringOrNull("before"), r.optStringOrNull("after"), r.optStringOrNull("result"))
                    }
                    sets[conjugation] = RuleSet(forms, obj.optBoolean("tetakei", false))
                }
                result[group] = sets
                return sets
            }

            for (group in root.keys()) resolve(group, emptySet())
            return result
        }

        fun parseWords(json: String, rules: Map<String, Map<String, RuleSet>>): List<Word> {
            val root = JSONObject(json)
            val words = ArrayList<Word>()
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                val group = obj.getString("group")
                val groupRules = rules[group] ?: continue
                val dictionary = obj.getString("dictionary")

                val conjugations = LinkedHashMap<String, Conjugation>()
                conjugations[DICTIONARY] = Conjugation(listOf(dictionary), tetakei = false)
                for ((name, ruleSet) in groupRules) {
                    conjugations[name] = conjugate(dictionary, ruleSet)
                }

                val sentences = obj.optJSONArray("sentences")
                val tags = obj.optJSONArray("tags")
                words += Word(
                    key = key,
                    group = group,
                    dictionary = dictionary,
                    meaning = obj.optString("meaning"),
                    sentenceJp = sentences?.optString(0).orEmpty(),
                    sentenceEn = sentences?.optString(1).orEmpty(),
                    tags = tags?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }.orEmpty(),
                    conjugations = conjugations,
                )
            }
            return words
        }

        fun conjugate(dictionary: String, ruleSet: RuleSet): Conjugation {
            val forms = ArrayList<String>()
            for (rule in ruleSet.forms) {
                if (rule.before != null && rule.after != null && dictionary.endsWith(rule.before)) {
                    forms += dictionary.dropLast(rule.before.length) + rule.after
                }
                if (rule.result != null) {
                    forms += rule.result
                }
            }
            return Conjugation(forms, ruleSet.tetakei)
        }

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (has(name) && !isNull(name)) getString(name) else null
    }
}
