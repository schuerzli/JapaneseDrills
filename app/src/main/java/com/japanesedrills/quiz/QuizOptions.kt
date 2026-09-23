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
     * Whether readings are shown above kanji, everywhere in the app. A display setting like
     * [theme], not a choice of what to practise, so it is not one of the [flags].
     */
    val furigana: Boolean = true,
    /**
     * The squares of the practice grid switched off one at a time, as `form|column` (see
     * [squareKey]). A square is off anyway when its form or its column is, so this holds
     * only the holes: the past of every class but い-adjectives, say.
     */
    val offSquares: Set<String> = emptySet(),
    /**
     * The word sets switched on, which the pool is drawn from ([WordSets]). Kept even while
     * [allWords] is on, so switching that off returns to the sets you had chosen.
     */
    val sets: Set<String> = emptySet(),
    /**
     * Draw on every word there is, whatever [sets] holds. It is a mode rather than a set of
     * its own: as one more set it would be the union of all the others and make them
     * meaningless while it was on.
     */
    val allWords: Boolean = true,
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

    /** Whether the grid asks [form] of the class column [column]. */
    fun asksSquare(form: String, column: String): Boolean =
        isOn(form) && COLUMNS_BY_KEY[column]?.groups?.any(::isOn) == true && squareKey(form, column) !in offSquares

    /** [form] of [column] switched on or off on its own, leaving the row and the column alone. */
    fun withSquare(form: String, column: String, value: Boolean): QuizOptions {
        val key = squareKey(form, column)
        return copy(offSquares = if (value) offSquares - key else offSquares + key)
    }

    /** Every group a column covers, switched together: a column is on when any of them is. */
    fun withColumn(column: WordColumn, value: Boolean): QuizOptions =
        copy(flags = flags + column.groups.associateWith { value })

    fun isColumnOn(column: WordColumn): Boolean = column.groups.any(::isOn)

    fun isSetOn(id: String): Boolean = id in sets

    fun withSet(id: String, value: Boolean): QuizOptions =
        copy(sets = if (value) sets + id else sets - id)

    /** True when the pool has no words at all to draw on, which is worth saying out loud. */
    val hasWords: Boolean get() = allWords || sets.isNotEmpty()

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
                    else -> on
                }
            },
            questionFocus = focus,
            // A preset states a whole selection, so it fills the grid in rather than
            // leaving yesterday's holes punched in it, and it draws on every word.
            offSquares = emptySet(),
            allWords = true,
        )

    /**
     * [preset] applied. [practisedForms] and [practisedGroups] are what the learner has
     * answered on the path so far, which only their progress can say.
     */
    fun withPreset(preset: PracticePreset, practisedForms: Set<String>, practisedGroups: Set<String>): QuizOptions =
        when (preset) {
            PracticePreset.Practised -> select(practisedForms, practisedGroups)
            // て and た share one fusion table, so the focus is the switch between them and the
            // forms without it. The godan column is where the fusions are, 行く included.
            PracticePreset.TeTa ->
                select(setOf("plain", "past", "te-form"), columnGroups("godan"), FOCUS_TETAKEI)
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
            FOCUS_TETAKEI -> listOf("te-form", "past")
            else -> TransformationBuilder.formsOfType(focus)
        }
        return needed.filter { it in FORM_KEYS }
            .fold(copy(questionFocus = focus)) { options, key -> options.with(key, true) }
    }

    /** True when both option sets produce the same question pool. */
    fun sameQuestions(other: QuizOptions): Boolean =
        flags == other.flags && questionFocus == other.questionFocus &&
            offSquares == other.offSquares && wordKeys == other.wordKeys &&
            allWords == other.allWords && (allWords || sets == other.sets)

    val questionCount: Int? get() = numQuestions.toIntOrNull()?.takeIf { it in 1..MAX_QUESTIONS }

    val kana: Boolean get() = isOn(KANA)
    val autoNext: Boolean get() = isOn(AUTO_NEXT)
    val autoExplain: Boolean get() = isOn(AUTO_EXPLAIN)
    val hasPoliteness: Boolean get() = isOn("plain") || isOn("polite")

    companion object {
        const val FOCUS_NONE = "none"
        const val FOCUS_TETAKEI = "tetakei"
        const val KANA = "kana"
        const val AUTO_NEXT = "go_to_next_question"
        const val AUTO_EXPLAIN = "auto_show_explanation"
        const val MAX_QUESTIONS = 999

        val FORMS = listOf(
            OptionItem("plain", "Plain"),
            OptionItem("polite", "Polite"),
            OptionItem("negative", "Negative"),
            OptionItem("past", "Past"),
            OptionItem("te-form", "て-form"),
            OptionItem("progressive", "Progressive"),
            OptionItem("desire", "Desire"),
            OptionItem("volitional", "Volitional"),
            OptionItem("potential", "Potential"),
            OptionItem("conditional", "Conditional (たら)"),
            OptionItem("provisional", "Provisional (ば)"),
            OptionItem("imperative", "Imperative"),
            OptionItem("passive", "Passive"),
            OptionItem("causative", "Causative"),
        )

        val REGULAR_VERBS = listOf(
            OptionItem("godan", "Godan verbs"),
            OptionItem("ichidan", "Ichidan verbs"),
        )

        /** The only two verbs irregular throughout, as the Conjugation Intro teaches them. */
        val IRREGULAR_VERBS = listOf(
            OptionItem("suru", "する verbs"),
            OptionItem("kuru", "来[く]る verb"),
        )

        /** Regular verbs with an exception worth knowing: 行く's て-form, ある's negative, いる's missing progressive. */
        val EXCEPTION_VERBS = listOf(
            OptionItem("iku", "行[い]く verb"),
            OptionItem("aru", "ある verb"),
            OptionItem("iru", "いる verb"),
        )

        val ADJECTIVES = listOf(
            OptionItem("i-adjective", "い-adjectives"),
            OptionItem("na-adjective", "な-adjectives"),
        )

        val IRREGULAR_ADJECTIVES = listOf(
            OptionItem("ii", "いい adjective"),
        )

        val GENERAL = listOf(
            OptionItem(TransformationBuilder.TRICK, "Trick questions (answers may be the same as the given form)"),
            OptionItem(KANA, "Use hiragana throughout the test (no kanji)"),
            OptionItem(AUTO_NEXT, "Automatically go to the next question on success"),
            OptionItem(AUTO_EXPLAIN, "Automatically show the explanation on error"),
        )

        val FOCUS = listOf(
            OptionItem(FOCUS_NONE, "None"),
            OptionItem("politeness", "Politeness"),
            OptionItem("negative", "Negative"),
            OptionItem("past", "Past"),
            OptionItem("te-form", "て-form"),
            OptionItem("progressive", "Progressive"),
            OptionItem("desire", "Desire"),
            OptionItem("volitional", "Volitional"),
            OptionItem("potential", "Potential"),
            OptionItem("conditional", "Conditional (たら)"),
            OptionItem("provisional", "Provisional (ば)"),
            OptionItem("imperative", "Imperative"),
            OptionItem("passive", "Passive"),
            OptionItem("causative", "Causative"),
            OptionItem(FOCUS_TETAKEI, "Godan て-form / past"),
        )

        /**
         * A column of the practice grid: a word class as the Grammar tab teaches it, over the
         * groups it covers. 行く and ある are godan verbs with an exception, いる an ichidan
         * one and いい an い-adjective, so each sits in its class's column rather than in one
         * of its own, and a column is a class a learner would name.
         */
        val COLUMNS = listOf(
            WordColumn("godan", "godan", setOf("godan", "iku", "aru")),
            WordColumn("ichidan", "ichidan", setOf("ichidan", "iru")),
            WordColumn("irregular", "する 来[く]る", setOf("suru", "kuru")),
            WordColumn("i-adjective", "い-adj", setOf("i-adjective", "ii")),
            WordColumn("na-adjective", "な-adj", setOf("na-adjective")),
        )

        private val COLUMNS_BY_KEY = COLUMNS.associateBy { it.key }
        private val COLUMN_OF_GROUP = COLUMNS.flatMap { column -> column.groups.map { it to column } }.toMap()

        /** The column a word's group belongs to, which is what the grid switches. */
        fun columnOf(group: String): String = COLUMN_OF_GROUP[group]?.key ?: group

        fun columnGroups(key: String): Set<String> = COLUMNS_BY_KEY[key]?.groups.orEmpty()

        /** How a switched-off square is named in [offSquares]. */
        fun squareKey(form: String, column: String): String = "$form|$column"

        /** Every option the start screen offers, in the order it shows them. */
        val ALL: List<OptionItem> =
            FORMS + REGULAR_VERBS + EXCEPTION_VERBS + IRREGULAR_VERBS + ADJECTIVES + IRREGULAR_ADJECTIVES +
                GENERAL

        /** The options that start switched on; every other option in [ALL] starts off. */
        private val ON_BY_DEFAULT = setOf(
            "plain", "negative", "past",
            "godan", "ichidan", "iku", "kuru", "suru", "iru", "aru",
            TransformationBuilder.TRICK,
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
            (REGULAR_VERBS + EXCEPTION_VERBS + IRREGULAR_VERBS + ADJECTIVES + IRREGULAR_ADJECTIVES)
                .map { it.key }.toSet()
    }
}

/** A word class the practice grid gives a column to; see [QuizOptions.COLUMNS]. */
data class WordColumn(val key: String, val label: String, val groups: Set<String>)

/** One-tap starting points for free practice, so the option grid is optional. */
enum class PracticePreset(val label: String) {
    /** Whatever the path has drilled so far; only offered once something has been answered there. */
    Practised("What I've learned"),
    TeTa("て-form and past"),
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
            furigana = prefs.getBoolean("furigana", defaults.furigana),
            offSquares = prefs.getStringSet("offSquares", null).orEmpty(),
            sets = prefs.getStringSet("sets", null).orEmpty(),
            allWords = prefs.getBoolean("allWords", defaults.allWords),
        )
    }

    fun save(options: QuizOptions) {
        prefs.edit().apply {
            options.flags.forEach { (key, value) -> putBoolean("flag_$key", value) }
            putString("questionFocus", options.questionFocus)
            putString("numQuestions", options.numQuestions)
            putString("theme", options.theme.name)
            putString("palette", options.palette.name)
            putBoolean("furigana", options.furigana)
            putStringSet("offSquares", options.offSquares)
            putStringSet("sets", options.sets)
            putBoolean("allWords", options.allWords)
        }.apply()
    }
}
