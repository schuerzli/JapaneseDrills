package com.japanesedrills.quiz

import android.content.Context

data class OptionItem(val key: String, val label: String)

/** Which colour scheme to use, regardless of the device setting. */
enum class ThemeChoice(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Which palette the app is dressed in. The names are tools/theme/schemes.py's palette keys:
 * the generated theme maps each to its colours with an exhaustive `when`, so a palette added
 * on one side and not the other fails to compile rather than crashing.
 */
enum class Palette(val label: String) {
    Latte("Latte"),
    Kissaten("Kissaten"),
    Washi("Washi"),
    Caramel("Caramel"),
    Mocha("Mocha"),
}

/** Settings chosen on the start screen. Flag keys match the web drill's option ids. */
data class QuizOptions(
    val flags: Map<String, Boolean> = DEFAULT_FLAGS,
    val questionFocus: String = FOCUS_NONE,
    val numQuestions: String = "10",
    /** Not a quiz setting, but it rides along to reuse the same persistence. */
    val theme: ThemeChoice = ThemeChoice.System,
    /** Rides along for the same reason as [theme]. */
    val palette: Palette = Palette.Latte,
    /**
     * Restricts the pool to these word keys. Null means "no restriction" and is what
     * free practice always uses; steps and review set it to pin their vocabulary.
     * Never persisted — it is derived from the learn path, not chosen by the user.
     */
    val wordKeys: Set<String>? = null,
) {
    fun isOn(key: String): Boolean = flags[key] ?: false

    /** Unknown tags never block a question, matching the web drill. */
    fun allows(tag: String): Boolean = flags[tag] != false

    fun with(key: String, value: Boolean): QuizOptions = copy(flags = flags + (key to value))

    /**
     * Exactly these forms and word groups, no level filter, and [focus]. The general
     * options (kana, furigana, trick questions…) are preferences rather than a choice of
     * what to practise, so they are left as they are.
     */
    fun select(forms: Set<String>, groups: Set<String>, focus: String = FOCUS_NONE): QuizOptions =
        copy(
            flags = flags.mapValues { (key, on) ->
                when (key) {
                    in FORM_KEYS -> key in forms
                    in GROUP_KEYS -> key in groups
                    in LEVEL_KEYS -> false
                    else -> on
                }
            },
            questionFocus = focus,
        )

    /**
     * [preset] applied. [practisedForms] and [practisedGroups] are what the learner has
     * answered on the path so far, which only their progress can say.
     */
    fun withPreset(preset: PracticePreset, practisedForms: Set<String>, practisedGroups: Set<String>): QuizOptions =
        when (preset) {
            PracticePreset.Practised -> select(practisedForms, practisedGroups)
            // て and た are one sound change, so the focus is the switch between them and the
            // forms without it. Godan is where the change is; 行く is its exception.
            PracticePreset.TeTa -> select(setOf("plain", "past", "te-form"), setOf("godan", "iku"), FOCUS_TETAKEI)
            PracticePreset.Everything -> select(FORM_KEYS, GROUP_KEYS)
        }

    /**
     * The forms [focus] asks about, switched on. Picking a focus whose form is off used to
     * leave the pool empty until the matching chip was found; a focus is a stronger
     * statement of intent than a chip left over from last time.
     */
    fun withFocus(focus: String): QuizOptions {
        val needed = when (focus) {
            FOCUS_NONE -> emptyList()
            "politeness" -> listOf("plain", "polite")
            FOCUS_TETAKEI -> listOf("te-form", "past")
            else -> listOf(focus)
        }
        return needed.filter { it in FORM_KEYS }
            .fold(copy(questionFocus = focus)) { options, key -> options.with(key, true) }
    }

    /** True when both option sets produce the same question pool. */
    fun sameQuestions(other: QuizOptions): Boolean =
        flags == other.flags && questionFocus == other.questionFocus && wordKeys == other.wordKeys

    val questionCount: Int? get() = numQuestions.toIntOrNull()?.takeIf { it in 1..MAX_QUESTIONS }

    val kana: Boolean get() = isOn(KANA)
    val furiganaAlways: Boolean get() = isOn(FURIGANA_ALWAYS)
    val autoNext: Boolean get() = isOn(AUTO_NEXT)
    val autoExplain: Boolean get() = isOn(AUTO_EXPLAIN)
    val hasPoliteness: Boolean get() = isOn("plain") || isOn("polite")

    companion object {
        const val FOCUS_NONE = "none"
        const val FOCUS_TETAKEI = "tetakei"
        const val KANA = "kana"
        const val FURIGANA_ALWAYS = "furigana_always"
        const val AUTO_NEXT = "go_to_next_question"
        const val AUTO_EXPLAIN = "auto_show_explanation"
        const val MAX_QUESTIONS = 999

        val FORMS = listOf(
            OptionItem("plain", "Plain"),
            OptionItem("polite", "Polite"),
            OptionItem("negative", "Negative"),
            OptionItem("past", "Past"),
            OptionItem("te-form", "て form"),
            OptionItem("progressive", "Progressive"),
            OptionItem("desire", "Desire"),
            OptionItem("volitional", "Volitional"),
            OptionItem("potential", "Potential"),
            OptionItem("conditional", "Conditional (たら)"),
            OptionItem("provisional", "Provisional conditional (ば)"),
            OptionItem("imperative", "Imperative"),
            OptionItem("passive", "Passive"),
            OptionItem("causative", "Causative"),
        )

        val REGULAR_VERBS = listOf(
            OptionItem("godan", "Godan verbs"),
            OptionItem("ichidan", "Ichidan verbs"),
        )

        val IRREGULAR_VERBS = listOf(
            OptionItem("suru", "する verbs"),
            OptionItem("kuru", "来る verb"),
            OptionItem("iku", "行く verb"),
            OptionItem("aru", "ある verb"),
            OptionItem("iru", "いる verbs"),
        )

        val ADJECTIVES = listOf(
            OptionItem("i-adjective", "い adjectives"),
            OptionItem("na-adjective", "な adjectives"),
        )

        val IRREGULAR_ADJECTIVES = listOf(
            OptionItem("ii", "いい adjective"),
        )

        // Levels come from the JLPT word lists; words outside them carry no level tag and
        // appear only when no filter is active. N1 is absent because the lists hold no
        // N1 verbs or adjectives this drill can conjugate.
        val LEVEL_FILTERS = listOf(
            OptionItem("common", "Top 100 common verbs"),
            OptionItem("n5", "JLPT N5"),
            OptionItem("n4", "JLPT N4"),
            OptionItem("n3", "JLPT N3"),
            OptionItem("n2", "JLPT N2"),
        )

        val GENERAL = listOf(
            OptionItem(TransformationBuilder.TRICK, "Trick questions (answers may be the same as the given form)"),
            OptionItem(KANA, "Use hiragana throughout the test (no kanji)"),
            OptionItem(FURIGANA_ALWAYS, "Always show furigana (otherwise tap a word to reveal it)"),
            OptionItem(AUTO_NEXT, "Automatically go to the next question on success"),
            OptionItem(AUTO_EXPLAIN, "Automatically show the explanation on error"),
        )

        val FOCUS = listOf(
            OptionItem(FOCUS_NONE, "None"),
            OptionItem("politeness", "Politeness"),
            OptionItem("negative", "Negative"),
            OptionItem("past", "Past"),
            OptionItem("te-form", "て form"),
            OptionItem("progressive", "Progressive"),
            OptionItem("desire", "Desire"),
            OptionItem("volitional", "Volitional"),
            OptionItem("potential", "Potential"),
            OptionItem("conditional", "Conditional (たら)"),
            OptionItem("provisional", "Provisional conditional (ば)"),
            OptionItem("imperative", "Imperative"),
            OptionItem("passive", "Passive"),
            OptionItem("causative", "Causative"),
            OptionItem(FOCUS_TETAKEI, "Godan て / た form"),
        )

        /**
         * A transformation type as the learner sees it. Types are the form keys except that
         * plain and polite share "politeness", a word no screen otherwise uses, so anything
         * that shows a type has to come through here rather than print the key.
         */
        fun typeLabel(type: String): String = FOCUS.firstOrNull { it.key == type }?.label ?: type

        /** Every option the start screen offers, in the order it shows them. */
        val ALL: List<OptionItem> =
            FORMS + REGULAR_VERBS + IRREGULAR_VERBS + ADJECTIVES + IRREGULAR_ADJECTIVES +
                LEVEL_FILTERS + GENERAL

        /** The options that start switched on; every other option in [ALL] starts off. */
        private val ON_BY_DEFAULT = setOf(
            "plain", "negative", "past",
            "godan", "ichidan", "iku", "kuru", "suru", "iru", "aru",
            TransformationBuilder.TRICK, FURIGANA_ALWAYS,
        )

        // Derived from ALL so an option can never be offered without a default behind it:
        // OptionsStore only restores keys that appear here.
        val DEFAULT_FLAGS: Map<String, Boolean> =
            ALL.associate { it.key to (it.key in ON_BY_DEFAULT) }

        // Which kind of thing each flag selects. The learn path needs to set the three
        // kinds independently, so they are named here rather than re-derived from the
        // display lists at every call site.
        val FORM_KEYS: Set<String> = FORMS.map { it.key }.toSet()
        val GROUP_KEYS: Set<String> =
            (REGULAR_VERBS + IRREGULAR_VERBS + ADJECTIVES + IRREGULAR_ADJECTIVES).map { it.key }.toSet()
        val LEVEL_KEYS: Set<String> = LEVEL_FILTERS.map { it.key }.toSet()
    }
}

/** One-tap starting points for free practice, so the option grid is optional. */
enum class PracticePreset(val label: String) {
    /** Whatever the path has drilled so far; only offered once something has been answered there. */
    Practised("What I've practised"),
    TeTa("て and た forms"),
    Everything("Everything"),
}

/** Persists [QuizOptions] between launches. */
class OptionsStore(context: Context) {

    private val prefs = context.getSharedPreferences("quiz_options", Context.MODE_PRIVATE)

    fun load(): QuizOptions {
        val defaults = QuizOptions()
        val flags = defaults.flags.mapValues { (key, default) -> prefs.getBoolean("flag_$key", default) }
        return QuizOptions(
            flags = flags,
            questionFocus = prefs.getString("questionFocus", null)
                ?.takeIf { focus -> QuizOptions.FOCUS.any { it.key == focus } }
                ?: defaults.questionFocus,
            numQuestions = prefs.getString("numQuestions", null) ?: defaults.numQuestions,
            theme = prefs.getString("theme", null)
                ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } }
                ?: defaults.theme,
            palette = prefs.getString("palette", null)
                ?.let { name -> Palette.entries.firstOrNull { it.name == name } }
                ?: defaults.palette,
        )
    }

    fun save(options: QuizOptions) {
        prefs.edit().apply {
            options.flags.forEach { (key, value) -> putBoolean("flag_$key", value) }
            putString("questionFocus", options.questionFocus)
            putString("numQuestions", options.numQuestions)
            putString("theme", options.theme.name)
            putString("palette", options.palette.name)
        }.apply()
    }
}
