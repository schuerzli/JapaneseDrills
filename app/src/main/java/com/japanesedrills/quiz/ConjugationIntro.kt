package com.japanesedrills.quiz

/**
 * The one piece of hand-written teaching in the app: how Japanese conjugation works at all,
 * which the per-form notes in [Grammar] assume you already know.
 *
 * It is prose, not data. Nothing here is derived from words.json or rules.json, so anything
 * it claims about a specific word has to stay true by hand — prefer stating the mechanism
 * and letting the Grammar reference show it on real words.
 *
 * Worked examples are written with marks ([RichPart.marked]): `書[か](く)` for the last kana,
 * `+ない` for the form's ending, `〈って〉` for the two fused. Prose is never read for marks.
 */
sealed interface ConjugationIntroBlock {

    /** A paragraph. Its Japanese is in furigana notation like everything else shown. */
    data class Line(val text: String) : ConjugationIntroBlock

    data class Bullet(val text: String) : ConjugationIntroBlock

    /** A heading inside a section, such as the two systems under godan. */
    data class Sub(val title: String) : ConjugationIntroBlock

    /** One worked change, [from] and [to] written with marks. [note] names the form, if given. */
    data class Step(val from: String, val to: String, val note: String = "") : ConjugationIntroBlock

    /**
     * [header] may be empty, in which case no header row is drawn, and a table with neither a
     * header nor marks is a grid of equal cells. [marked] cells are worked examples.
     * [firstColumnEnd] right-aligns the first column against the next, as endings are set
     * against what they become; a column of names reads better left-aligned.
     */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val marked: Boolean = false,
        val firstColumnEnd: Boolean = true,
    ) : ConjugationIntroBlock

    /** Says what the marks in the examples mean, shown in the marks themselves. */
    data class Legend(val parts: List<RichPart>) : ConjugationIntroBlock
}

data class ConjugationIntroSection(val title: String, val blocks: List<ConjugationIntroBlock>)

object ConjugationIntro {

    const val TITLE = "Conjugation Intro"
    const val SUMMARY = "Forms, the last kana, the verb classes and the systems every form is built with."

    val SECTIONS: List<ConjugationIntroSection> = listOf(
        ConjugationIntroSection(
            "The last kana",
            listOf(
                ConjugationIntroBlock.Line(
                    "When we talk about Japanese conjugation, what we mean is changing a verb or an " +
                        "adjective to change its meaning. That can mean changing it from present to past, " +
                        "or it can be more subtle, as with the て-form, which has many different uses. As " +
                        "the name て-form suggests, we call these states that verbs and adjectives can take " +
                        "on \"forms\". Here are some examples:",
                ),
                ConjugationIntroBlock.Bullet("the negative form"),
                ConjugationIntroBlock.Bullet("the past form"),
                ConjugationIntroBlock.Bullet("the て-form"),
                ConjugationIntroBlock.Bullet("the progressive form"),
                ConjugationIntroBlock.Bullet("the desire form"),
                ConjugationIntroBlock.Bullet("the polite form"),
                ConjugationIntroBlock.Line(
                    "Japanese conjugation is remarkably regular. It revolves almost entirely around the " +
                        "very last kana of a verb or adjective: when we change form, only the last kana of " +
                        "the dictionary form, the form a word is listed under, is ever affected. We call " +
                        "the part that never changes the stem.",
                ),
                ConjugationIntroBlock.Line(
                    "Every form has an ending associated with it. For example, the て-form's ending is " +
                        "て. When we change a verb to that form, we do something with the last kana of the " +
                        "word to attach the form's ending. There are a total of three ways to attach a " +
                        "form ending to verbs and two ways for adjectives, plus a very small number of " +
                        "irregular verbs and a single irregular adjective.",
                ),
            ),
        ),

        ConjugationIntroSection(
            "Verb classes",
            listOf(
                ConjugationIntroBlock.Line(
                    "Verbs are generally talked about as belonging to one of three classes. How a verb " +
                        "changes when it changes form decides which class it belongs to. The three " +
                        "classes are:",
                ),
                ConjugationIntroBlock.Bullet("一[いち]段[だん] (ichidan) verbs"),
                ConjugationIntroBlock.Bullet("五[ご]段[だん] (godan) verbs"),
                ConjugationIntroBlock.Bullet("irregular verbs"),
                ConjugationIntroBlock.Line(
                    "Irregular verbs aside, that leaves two classes and three ways to attach a form " +
                        "ending. How does that work? 一[いち]段[だん] verbs change in a single way for every " +
                        "form. 五[ご]段[だん] verbs use one system for some forms and another system for the " +
                        "rest. So let's get into the specifics of those systems now.",
                ),
            ),
        ),

        ConjugationIntroSection(
            "一[いち]段[だん] (ichidan) verbs",
            listOf(
                ConjugationIntroBlock.Line(
                    "一[いち]段[だん] verbs are extremely simple and regular. They always change in the same " +
                        "way, whatever the form. All 一[いち]段[だん] verbs end in either -いる or -える in " +
                        "their dictionary forms. This means they end in any kana with an i or an e vowel, " +
                        "followed by る: 見[み]る, 食[た]べる, 信[しん]じる, 出[で]る.",
                ),
                ConjugationIntroBlock.Line(
                    "Be aware that all 一[いち]段[だん] verbs end in -いる or -える, but not all verbs that " +
                        "end in -いる or -える are 一[いち]段[だん] verbs. 帰[かえ]る, 入[はい]る, 走[はし]る, " +
                        "知[し]る, 滑[すべ]る and 参[まい]る are all 五[ご]段[だん] verbs. This is just something " +
                        "you learn on a case-by-case basis. The rule that does hold without exception runs " +
                        "the other way: a verb that does not end in る is always 五[ご]段[だん].",
                ),
                ConjugationIntroBlock.Line(
                    "To change a 一[いち]段[だん] verb, all you do is drop the last kana of the dictionary " +
                        "form, which is always る, and add the form's ending.",
                ),
                ConjugationIntroBlock.Legend(
                    listOf(
                        RichPart.Text("In the examples, the last kana is marked like "),
                        RichPart.Marked("る", Mark.LastKana),
                        RichPart.Text(", the form's ending like "),
                        RichPart.Marked("ない", Mark.Ending),
                        RichPart.Text(", and the two fused into one like "),
                        RichPart.Marked("って", Mark.Fused),
                        RichPart.Text("."),
                    ),
                ),
                ConjugationIntroBlock.Step("食[た]べ(る)", "食[た]べ+ない"),
                ConjugationIntroBlock.Step("食[た]べ(る)", "食[た]べ+た"),
                ConjugationIntroBlock.Step("食[た]べ(る)", "食[た]べ+て"),
                ConjugationIntroBlock.Line("The stem 食[た]べ never changes."),
                ConjugationIntroBlock.Line(
                    "A few forms have an ending of their own for 一[いち]段[だん] verbs: the potential and " +
                        "the passive take られる, the causative させる, the volitional よう, the " +
                        "imperative ろ and the provisional れば.",
                ),
                ConjugationIntroBlock.Step("食[た]べ(る)", "食[た]べ+られる"),
                ConjugationIntroBlock.Line("Each form's page in the Grammar tab shows its ending for every class."),
            ),
        ),

        ConjugationIntroSection(
            "五[ご]段[だん] (godan) verbs",
            listOf(
                ConjugationIntroBlock.Line(
                    "五[ご]段[だん] verbs are a little more complicated, and there are two different systems " +
                        "for conjugation, depending on the target form. We will call these two systems the " +
                        "row-shift system and the fusion system. But to understand these systems, and " +
                        "Japanese conjugation in general, we need a little bit of context first.",
                ),
                ConjugationIntroBlock.Sub("The kana grid"),
                ConjugationIntroBlock.Line(
                    "To understand Japanese verb conjugation, we must think of the last kana of a verb " +
                        "as living on this grid.",
                ),
                ConjugationIntroBlock.Table(
                    header = emptyList(),
                    rows = listOf(
                        listOf("あ", "か", "が", "さ", "た", "な", "ば", "ま", "ら"),
                        listOf("い", "き", "ぎ", "し", "ち", "に", "び", "み", "り"),
                        listOf("う", "く", "ぐ", "す", "つ", "ぬ", "ぶ", "む", "る"),
                        listOf("え", "け", "げ", "せ", "て", "ね", "べ", "め", "れ"),
                        listOf("お", "こ", "ご", "そ", "と", "の", "ぼ", "も", "ろ"),
                    ),
                ),
                ConjugationIntroBlock.Line(
                    "We call each row in the grid by its kana in the first column: the あ-row, the " +
                        "い-row, and so on down to the お-row. There are two things to note about this grid:",
                ),
                ConjugationIntroBlock.Line(
                    "1. Every Japanese verb, in its dictionary form, ends in a kana from the う-row of " +
                        "this grid. No exceptions.",
                ),
                ConjugationIntroBlock.Line(
                    "2. There are exactly five rows in it, which is where 五[ご]段[だん], \"five rows\", " +
                        "gets its name.",
                ),
                ConjugationIntroBlock.Sub("The row-shift system"),
                ConjugationIntroBlock.Line(
                    "When we change a 五[ご]段[だん] verb to a form that uses the row-shift system, we do not " +
                        "drop the last kana of the dictionary form. Rather, we shift it up or down to a " +
                        "different row in the same column. Which row we shift it to depends on the form we " +
                        "are changing to.",
                ),
                ConjugationIntroBlock.Line(
                    "For the negative, we shift the last kana from the う-row to the あ-row and add the " +
                        "negative ending ない.",
                ),
                ConjugationIntroBlock.Step("書[か](く)", "書[か](か)+ない"),
                ConjugationIntroBlock.Line("The う-row く turns into the あ-row kana from the same column, か."),
                ConjugationIntroBlock.Line("Other forms use other rows. たい takes the い-row, and so does the polite ます form."),
                ConjugationIntroBlock.Step("書[か](く)", "書[か](き)+たい"),
                ConjugationIntroBlock.Line(
                    "Five rows, five kana the last kana of the word can be: that is the 五[ご] in " +
                        "五[ご]段[だん]. It also explains why 一[いち]段[だん] verbs are called 一[いち]段[だん], " +
                        "or \"one row\" verbs. Since they drop their last kana when they change, they can " +
                        "only ever be in one row: the row of the last kana of the stem, like べ in 食[た]べる.",
                ),
                ConjugationIntroBlock.Line(
                    "This system is extremely regular. There is only one special case in the whole " +
                        "shift logic: when a verb in dictionary form ends in the actual kana う, its " +
                        "あ-row kana is わ, not あ.",
                ),
                ConjugationIntroBlock.Step("買[か](う)", "買[か](わ)+ない"),
                ConjugationIntroBlock.Line(
                    "The following forms all use the row-shift system, each with its row and ending. So " +
                        "does everything built on them: the negative's compounds, and every polite form. " +
                        "The imperative is the one with no ending at all.",
                ),
                ConjugationIntroBlock.Table(
                    header = listOf("form", "row", "ending"),
                    rows = listOf(
                        listOf("negative", "あ", "ない"),
                        listOf("passive", "あ", "れる"),
                        listOf("causative", "あ", "せる"),
                        listOf("polite", "い", "ます"),
                        listOf("desire", "い", "たい"),
                        listOf("provisional", "え", "ば"),
                        listOf("potential", "え", "る"),
                        listOf("imperative", "え", "—"),
                        listOf("volitional", "お", "う"),
                    ),
                    firstColumnEnd = false,
                ),
                ConjugationIntroBlock.Sub("The fusion system"),
                ConjugationIntroBlock.Line(
                    "In this system, rather than modifying the last kana and adding an ending, the " +
                        "form's ending fuses with the last kana. How they fuse depends on the specific " +
                        "last kana of the dictionary form, and must be memorized. This table shows all " +
                        "fusions for all nine possible う-row kana, for the て-form and the past form. " +
                        "行[い]く is the one exception, and is covered below.",
                ),
                ConjugationIntroBlock.Table(
                    header = listOf("dictionary", "て-form", "past"),
                    rows = listOf(
                        listOf("う · つ · る", "って", "った"),
                        listOf("ぬ · ぶ · む", "んで", "んだ"),
                        listOf("く", "いて", "いた"),
                        listOf("ぐ", "いで", "いだ"),
                        listOf("す", "して", "した"),
                    ),
                ),
                ConjugationIntroBlock.Table(
                    header = emptyList(),
                    marked = true,
                    rows = listOf(
                        listOf("買[か](う)", "買[か]〈って〉", "買[か]〈った〉"),
                        listOf("待[ま](つ)", "待[ま]〈って〉", "待[ま]〈った〉"),
                        listOf("取[と](る)", "取[と]〈って〉", "取[と]〈った〉"),
                        listOf("死[し](ぬ)", "死[し]〈んで〉", "死[し]〈んだ〉"),
                        listOf("遊[あそ](ぶ)", "遊[あそ]〈んで〉", "遊[あそ]〈んだ〉"),
                        listOf("読[よ](む)", "読[よ]〈んで〉", "読[よ]〈んだ〉"),
                        listOf("書[か](く)", "書[か]〈いて〉", "書[か]〈いた〉"),
                        listOf("泳[およ](ぐ)", "泳[およ]〈いで〉", "泳[およ]〈いだ〉"),
                        listOf("話[はな](す)", "話[はな]〈して〉", "話[はな]〈した〉"),
                    ),
                ),
                ConjugationIntroBlock.Line("That's it. You will have to memorize which fusion applies to which last kana."),
                ConjugationIntroBlock.Line("The following forms use the fusion system:"),
                ConjugationIntroBlock.Bullet("the て-form"),
                ConjugationIntroBlock.Bullet("the past"),
                ConjugationIntroBlock.Bullet("the conditional: the past + ら, 書[か]いたら"),
                ConjugationIntroBlock.Bullet("every form built on the て-form, such as the progressive: 書[か]いている"),
            ),
        ),

        ConjugationIntroSection(
            "Irregular verbs",
            listOf(
                ConjugationIntroBlock.Line("There are only two verbs in Japanese that are irregular throughout:"),
                ConjugationIntroBlock.Bullet("する (to do) — し, さ and すれ all appear: しない, して, させる, すれば"),
                ConjugationIntroBlock.Bullet("来[く]る (to come) — the reading itself changes: こない, きた, きて"),
                ConjugationIntroBlock.Line("Three more are regular verbs with exceptions worth knowing:"),
                ConjugationIntroBlock.Bullet(
                    "行[い]く — 五[ご]段[だん] in every way, except that its て-form and past are 行[い]って " +
                        "and 行[い]った, not 行[い]いて",
                ),
                ConjugationIntroBlock.Bullet(
                    "ある — its negative is ない, not あらない. It also has no imperative, potential, " +
                        "passive or causative",
                ),
                ConjugationIntroBlock.Bullet("いる — already a state, so it is not put into the progressive ている"),
            ),
        ),

        ConjugationIntroSection(
            "Adjectives",
            listOf(
                ConjugationIntroBlock.Line(
                    "Japanese adjectives conjugate in two different ways, plus a single irregular " +
                        "adjective.",
                ),
                ConjugationIntroBlock.Sub("い-adjectives"),
                ConjugationIntroBlock.Line(
                    "い-adjectives change much like 一[いち]段[だん] verbs: they drop their last kana, い, " +
                        "and add an ending. The endings are their own, though, not the ones verbs take:",
                ),
                ConjugationIntroBlock.Step("高[たか](い)", "高[たか]+くない"),
                ConjugationIntroBlock.Step("高[たか](い)", "高[たか]+かった"),
                ConjugationIntroBlock.Step("高[たか](い)", "高[たか]+くて"),
                ConjugationIntroBlock.Sub("な-adjectives"),
                ConjugationIntroBlock.Line(
                    "な-adjectives do not conjugate at all. Instead, they appear with the copula だ " +
                        "after them, and that is conjugated:",
                ),
                ConjugationIntroBlock.Step("便[べん]利[り](だ)", "便[べん]利[り]+じゃない"),
                ConjugationIntroBlock.Step("便[べん]利[り](だ)", "便[べん]利[り]+だった"),
                ConjugationIntroBlock.Step("便[べん]利[り](だ)", "便[べん]利[り]+で"),
                ConjugationIntroBlock.Line(
                    "The negative has a more formal version, 便[べん]利[り]ではない. な-adjectives are " +
                        "named for the form they take before a noun, 便[べん]利[り]な人[ひと].",
                ),
                ConjugationIntroBlock.Sub("いい"),
                ConjugationIntroBlock.Line("いい switches to よ- the moment it conjugates:"),
                ConjugationIntroBlock.Step("いい", "よくない"),
                ConjugationIntroBlock.Step("いい", "よかった"),
                ConjugationIntroBlock.Step("いい", "よくて"),
            ),
        ),

        ConjugationIntroSection(
            "Combining forms",
            listOf(
                ConjugationIntroBlock.Line(
                    "Some forms do not stop after one change. For example, you might want the negative " +
                        "of the potential form. In this case, true to form, Japanese is rather simple and " +
                        "regular. You first construct one form, then treat that as the new dictionary form " +
                        "to construct the next.",
                ),
                ConjugationIntroBlock.Step("書[か](く)", "書[か](け)+る", "potential"),
                ConjugationIntroBlock.Step("書[か]け(る)", "書[か]け+ない", "negative"),
                ConjugationIntroBlock.Line("write → can write → cannot write"),
                ConjugationIntroBlock.Line("The same goes for:"),
                ConjugationIntroBlock.Step("書[か](く)", "書[か](か)+れる", "passive"),
                ConjugationIntroBlock.Step("書[か](く)", "書[か](か)+せる", "causative"),
                ConjugationIntroBlock.Step("書[か](く)", "書[か]〈いて〉+いる", "progressive"),
                ConjugationIntroBlock.Line(
                    "Every one of these new words ends in -える or -いる, and is treated as a " +
                        "一[いち]段[だん] verb from then on. So 書[か]ける goes to 書[か]けない and 書[か]けた " +
                        "exactly the way 食[た]べる goes to 食[た]べない and 食[た]べた. The same is true of the " +
                        "一[いち]段[だん] and irregular versions: 食[た]べられる, させる and 来[こ]させる are all " +
                        "一[いち]段[だん] verbs too.",
                ),
                ConjugationIntroBlock.Line(
                    "In 書[か]く → 書[か]きたい (desire), たい is not a verb but an い-adjective. It behaves " +
                        "like 高[たか]い: 書[か]きたくない, 書[か]きたかった.",
                ),
                ConjugationIntroBlock.Line(
                    "This is why a text this short covers every form in this app's Grammar tab. A long " +
                        "form is a short form with another short form applied to it, and the second step " +
                        "follows rules you already know.",
                ),
            ),
        ),

        ConjugationIntroSection(
            "What comes next",
            listOf(
                ConjugationIntroBlock.Line(
                    "Every form in the Grammar tab is one of these changes plus an ending. Once the row " +
                        "shifts and the fusion table are mastered, the rest is mostly vocabulary.",
                ),
            ),
        ),
    )
}
