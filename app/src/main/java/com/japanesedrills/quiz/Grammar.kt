package com.japanesedrills.quiz

import com.japanesedrills.data.Word

/**
 * What a form *means*, for the Grammar reference and the lesson that introduces it.
 *
 * Only the prose lives here. How a form is built is derived per word from [Explanations],
 * so the construction shown can never drift from the answers the drill accepts — and the
 * examples are real entries from words.json rather than a second, hand-kept copy.
 */
data class GrammarNote(
    /** Matches a [QuizOptions.FORMS] key, which is also the conjugation name. */
    val key: String,
    val title: String,
    /** One line: what the form does. */
    val summary: String,
    /** When you would actually reach for it. */
    val notes: List<String>,
)

/**
 * The words the construction section builds its derivations from, and which of them are
 * irregular for a given form rather than irregular in general.
 */
class GrammarExamples(
    private val words: Map<String, Word> = emptyMap(),
    private val ownForms: Map<String, Set<String>> = emptyMap(),
) {
    operator fun get(key: String): Word? = words[key]

    /** True when [word]'s class defines its own rule for [target] instead of inheriting one. */
    fun declaresOwnRule(word: Word, target: String): Boolean = target in ownForms[word.group].orEmpty()
}

object Grammar {

    /**
     * Example words for the construction section. Ichidan leads because it is the class
     * with no table and no exceptions; godan follows, then the irregulars, then the
     * adjectives. A class is skipped for a form it does not have, which is how adjectives
     * quietly drop out of the verb-only forms.
     */
    private const val ICHIDAN = "食べる"
    private val GODAN = listOf("書く")
    private val IRREGULAR = listOf("する", "来る", "行く")
    private val ADJECTIVES = listOf("高い", "便利な")

    /** 書く is the ordinary shift; 買う is the one exception, う to わ rather than あ. */
    private val A_ROW = listOf("書く", "買う")

    private val GODAN_BY_FORM = mapOf(
        "negative" to A_ROW,
        "passive" to A_ROW,
        "causative" to A_ROW,
    )

    /** Shown under one heading, because "irregular" is the useful fact about all of them. */
    val IRREGULAR_GROUPS = setOf("suru", "kuru", "iku")

    /** The example words to build [formKey] with, in the order they should be shown. */
    fun examplesFor(formKey: String): List<String> =
        listOf(ICHIDAN) + (GODAN_BY_FORM[formKey] ?: GODAN) + IRREGULAR + ADJECTIVES

    /** Every word any form might need, for loading them once. */
    val EXAMPLE_KEYS: List<String> =
        (listOf(ICHIDAN) + GODAN + A_ROW + IRREGULAR + ADJECTIVES).distinct()

    val NOTES: List<GrammarNote> = listOf(
        GrammarNote(
            key = "plain",
            title = "Plain form",
            summary = "The dictionary form — how a word is listed, and how it is said casually.",
            notes = listOf(
                "Used with family, close friends and anyone below you in a hierarchy.",
                "The normal form inside a longer sentence: before と思う, から, けど, and " +
                    "directly in front of a noun.",
                "The standard style for most writing that is not a letter — news, novels, notes.",
            ),
        ),
        GrammarNote(
            key = "polite",
            title = "Polite form",
            summary = "The ます form: the safe, neutral register for anyone you are not close to.",
            notes = listOf(
                "Default with strangers, colleagues, shop staff and teachers.",
                "Politeness is carried by the final verb, so only the end of a sentence changes.",
                "Neither rude nor humble — it is the unmarked choice when in doubt.",
            ),
        ),
        GrammarNote(
            key = "negative",
            title = "Negative",
            summary = "Says that something does not or will not happen.",
            notes = listOf(
                "Plain ない and polite ません mean the same thing at different politeness levels.",
                "The plain negative behaves like an い-adjective, which is why its past is なかった.",
                "With a verb it is \"does not\"; with an adjective, \"is not\".",
            ),
        ),
        GrammarNote(
            key = "past",
            title = "Past",
            summary = "Something already happened, or a state that held before now.",
            notes = listOf(
                "Japanese has no separate perfect tense: 食べた covers \"ate\" and \"have eaten\".",
                "The plain past uses the same sound changes as the て form, so learning one " +
                    "gives you the other.",
                "In front of a noun the plain past describes it: 買った本 — the book I bought.",
            ),
        ),
        GrammarNote(
            key = "te-form",
            title = "て form",
            summary = "Not a tense at all — the connector most later grammar is built on.",
            notes = listOf(
                "Joins clauses: \"do this, and then that\", with the tense set by the final verb.",
                "Makes a request with ください.",
                "Carries ている (ongoing), てもいい (permission), てから (after) and much more.",
                "Godan verbs change sound here in ways that have to be learned as a set.",
            ),
        ),
        GrammarNote(
            key = "progressive",
            title = "Progressive",
            summary = "ている: an action in progress, or the state left behind by one.",
            notes = listOf(
                "With action verbs it is \"is doing\": food is being eaten right now.",
                "With change-of-state verbs it is the resulting state, not the change: " +
                    "知っている means \"know\", 結婚している means \"is married\".",
                "Also covers habits — what someone does these days.",
                "Casual speech drops the い: 食べてる.",
            ),
        ),
        GrammarNote(
            key = "desire",
            title = "Desire",
            summary = "たい: wanting to do something.",
            notes = listOf(
                "About your own wishes, or a question about the listener's.",
                "Stating a third person's desire needs たがる instead — たい is not used for it.",
                "The result conjugates as an い-adjective: 食べたくない, 食べたかった.",
                "The object may take が as well as を.",
            ),
        ),
        GrammarNote(
            key = "volitional",
            title = "Volitional",
            summary = "\"Let's\", or a decision you are announcing to yourself.",
            notes = listOf(
                "Polite ましょう is the everyday \"let's\"; plain よう/おう is casual.",
                "With と思う it becomes an intention: \"I think I'll…\".",
                "Not a request — for that, use the て form with ください.",
            ),
        ),
        GrammarNote(
            key = "potential",
            title = "Potential",
            summary = "Being able to do something.",
            notes = listOf(
                "What would be the object often takes が rather than を.",
                "Ichidan potential looks identical to the passive; context separates them.",
                "Speech commonly drops the ら — 見れる for 見られる — though it is still " +
                    "considered informal in writing.",
                "する has its own word for this: できる.",
            ),
        ),
        GrammarNote(
            key = "conditional",
            title = "Conditional (たら)",
            summary = "\"If\" or \"when\" — the most flexible of the conditionals.",
            notes = listOf(
                "Works for one-off and hypothetical conditions alike.",
                "In the past it can mean \"when I did X, it turned out that…\" — a discovery " +
                    "rather than a condition.",
                "Built straight from the past form, so the sound changes are ones you know.",
            ),
        ),
        GrammarNote(
            key = "provisional",
            title = "Provisional (ば)",
            summary = "\"If\" for general rules and hypotheticals.",
            notes = listOf(
                "At home in proverbs and general truths: if you do X, Y follows.",
                "Prefers stating a condition over sequencing two events — that is たら's job.",
                "ばよかった is the standard way to say \"I should have…\".",
            ),
        ),
        GrammarNote(
            key = "imperative",
            title = "Imperative",
            summary = "A blunt order. Strong enough that it is rarely used to someone's face.",
            notes = listOf(
                "Heard in anger, in sports and military speech, and on signs: 止まれ.",
                "Common when quoting an order indirectly, where the rudeness does not land.",
                "The negative is the plain form plus な: 行くな, don't go.",
                "For an ordinary request use the て form with ください instead.",
            ),
        ),
        GrammarNote(
            key = "passive",
            title = "Passive",
            summary = "Something is done to the subject, with the doer marked by に.",
            notes = listOf(
                "Also the \"suffering passive\": it happened to me and I am worse off for it — " +
                    "a use with no direct English equivalent.",
                "Doubles as an honorific: the same form can raise the listener rather than " +
                    "passivise the verb.",
                "Identical in shape to the ichidan potential.",
            ),
        ),
        GrammarNote(
            key = "causative",
            title = "Causative",
            summary = "Making or letting someone else do something.",
            notes = listOf(
                "\"Make\" and \"let\" are the same form — the particle and the context decide.",
                "The person made to act is marked by に, or を when they have no say.",
                "With てください it becomes asking permission: 行かせてください, please let me go.",
            ),
        ),
    )

    private val byKey = NOTES.associateBy { it.key }

    operator fun get(key: String): GrammarNote? = byKey[key]

    /**
     * The conjugation to derive when showing how a form is built, or null when there is
     * nothing to derive — the plain form *is* the dictionary form.
     */
    fun conjugationOf(formKey: String): String? = formKey.takeIf { it != "plain" }
}
