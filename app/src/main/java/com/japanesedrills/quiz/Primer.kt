package com.japanesedrills.quiz

/**
 * The one piece of hand-written teaching in the app: how Japanese conjugation works at all,
 * which the per-form notes in [Grammar] assume you already know.
 *
 * It is prose, not data. Nothing here is derived from words.json or rules.json, so anything
 * it claims about a specific word has to stay true by hand — prefer stating the mechanism
 * and letting the Grammar reference show it on real words.
 */
sealed interface PrimerBlock {

    /** A paragraph. Its Japanese is in furigana notation like everything else shown. */
    data class Line(val text: String) : PrimerBlock

    data class Bullet(val text: String) : PrimerBlock

    /** A heading inside a section, for the two systems under godan. */
    data class Sub(val title: String) : PrimerBlock

    /** One worked change, rendered the way the Grammar reference renders its derivations. */
    data class Step(val from: String, val to: String, val note: String = "") : PrimerBlock

    /** [header] may be empty, in which case no header row is drawn. */
    data class Table(val header: List<String>, val rows: List<List<String>>) : PrimerBlock
}

data class PrimerSection(val title: String, val blocks: List<PrimerBlock>)

object Primer {

    const val TITLE = "How conjugation works"
    const val SUMMARY = "The last kana, the kana grid, and the two systems every form is built with."

    val SECTIONS: List<PrimerSection> = listOf(
        PrimerSection(
            "The last kana",
            listOf(
                PrimerBlock.Line(
                    "Japanese conjugation is remarkably regular. It revolves almost entirely around " +
                        "the very last kana of a verb or adjective: when we change form, only that " +
                        "last kana of the plain form is ever affected. The rest, the part that stays " +
                        "the same, is called the stem.",
                ),
                PrimerBlock.Line(
                    "All forms are constructed in one of two ways, which we will informally call the " +
                        "row-shift system and the fusion system.",
                ),
                PrimerBlock.Sub("The row-shift system"),
                PrimerBlock.Line("Modify the last kana, then add an ending depending on the form."),
                PrimerBlock.Step("書[か]く", "書[か]かない"),
                PrimerBlock.Line(
                    "The stem 書[か] stays the same, the last kana く becomes か, and we add the ending ない.",
                ),
                PrimerBlock.Sub("The fusion system"),
                PrimerBlock.Line(
                    "Fuse the last kana and the ending together in one of a number of predefined ways.",
                ),
                PrimerBlock.Step("買[か]う", "買[か]って"),
                PrimerBlock.Line(
                    "The stem 買[か] stays the same, and the last kana う and the ending て fuse to become って.",
                ),
            ),
        ),

        PrimerSection(
            "Verb classes",
            listOf(
                PrimerBlock.Line(
                    "Verbs are generally talked about as belonging to one of three classes. A verb's " +
                        "class defines how it changes when it changes form. The classes are 一[いち]段[だん] " +
                        "(ichidan) verbs, 五[ご]段[だん] (godan) verbs, and a handful of irregular verbs.",
                ),
                PrimerBlock.Line("Adjectives work differently again, and are covered at the end."),
            ),
        ),

        PrimerSection(
            "The kana grid",
            listOf(
                PrimerBlock.Line(
                    "To understand Japanese conjugation, we must think of the last kana of a verb as " +
                        "living on this grid.",
                ),
                PrimerBlock.Table(
                    header = emptyList(),
                    rows = listOf(
                        listOf("あ", "か", "が", "さ", "た", "な", "ば", "ま", "ら"),
                        listOf("い", "き", "ぎ", "し", "ち", "に", "び", "み", "り"),
                        listOf("う", "く", "ぐ", "す", "つ", "ぬ", "ぶ", "む", "る"),
                        listOf("え", "け", "げ", "せ", "て", "ね", "べ", "め", "れ"),
                        listOf("お", "こ", "ご", "そ", "と", "の", "ぼ", "も", "ろ"),
                    ),
                ),
                PrimerBlock.Line(
                    "Each row is named after its kana in the first column: the あ-row, the い-row, " +
                        "and so on down to the お-row.",
                ),
                PrimerBlock.Line(
                    "Insight 1: every Japanese verb, in its plain form, ends in a kana from the " +
                        "う-row of this grid. No exceptions.",
                ),
            ),
        ),

        PrimerSection(
            "一[いち]段[だん] (ichidan) verbs",
            listOf(
                PrimerBlock.Line(
                    "Ichidan verbs all end in either -いる or -える in their plain forms. This means " +
                        "they end in any kana with an i or an e vowel, taken from the full gojūon, " +
                        "followed by る: 見[み]る, 食[た]べる, 信[しん]じる, 出[で]る.",
                ),
                PrimerBlock.Line(
                    "To change their form, all you do is drop the last kana of the plain form, which " +
                        "is always る, and add the ending.",
                ),
                PrimerBlock.Step("食[た]べる", "食[た]べない"),
                PrimerBlock.Step("食[た]べる", "食[た]べた"),
                PrimerBlock.Step("食[た]べる", "食[た]べて"),
                PrimerBlock.Line("The stem 食[た]べ never changes."),
                PrimerBlock.Line(
                    "Be aware: all ichidan verbs end in -いる or -える, but not all verbs that end in " +
                        "-いる or -える are ichidan verbs. 帰[かえ]る, 入[はい]る, 走[はし]る, 知[し]る, 滑[すべ]る and 参[まい]る are all " +
                        "godan verbs. This is just something you learn on a case-by-case basis.",
                ),
                PrimerBlock.Line(
                    "The rule that does hold without exception runs the other way: a verb that does " +
                        "not end in る is always godan.",
                ),
            ),
        ),

        PrimerSection(
            "五[ご]段[だん] (godan) verbs",
            listOf(
                PrimerBlock.Line(
                    "五[ご]段[だん] verbs are a little more complicated, and there are two different systems " +
                        "for conjugation, depending on the target form.",
                ),
                PrimerBlock.Sub("The row-shift system"),
                PrimerBlock.Line(
                    "五[ご]段[だん] means \"five rows\". With this in mind, we can go back to the kana grid and " +
                        "reveal the second insight: there are exactly five rows in it.",
                ),
                PrimerBlock.Line(
                    "When we change the form of a 五[ご]段[だん] verb, we do not drop the last kana of the plain " +
                        "form. Rather, we shift it up or down to a different row in the same column. " +
                        "Which row we shift it to depends on the form we are changing to. For the " +
                        "negative, we shift the last kana from the う-row to the あ-row and add ない. " +
                        "In 書[か]く, the う-row く turns into the あ-row kana from the same column, か.",
                ),
                PrimerBlock.Step("書[か]く", "書[か]かない"),
                PrimerBlock.Line(
                    "Other forms use other rows. たい takes the い-row, and so does the polite ます " +
                        "form, which is why that い-row form is worth knowing as a stem of its own.",
                ),
                PrimerBlock.Step("書[か]く", "書[か]きたい"),
                PrimerBlock.Line(
                    "Five rows, five kana the last kana of the word can be, so: 五[ご]段[だん]. This also " +
                        "explains why 一[いち]段[だん] verbs are called 一[いち]段[だん], or \"one row\" verbs. Since they drop " +
                        "their last kana when they change, they can only ever be in one: the row of " +
                        "the last kana of the stem, べ in 食[た]べる.",
                ),
                PrimerBlock.Line(
                    "This system is extremely regular, and there is only one exception in the whole " +
                        "shift logic: when a verb in plain form ends in the actual kana う, its あ-row " +
                        "kana is わ, not あ.",
                ),
                PrimerBlock.Step("買[か]う", "買[か]わない"),
                PrimerBlock.Line(
                    "These forms all use the row-shift system: the negative and all its compounds, " +
                        "the passive, the causative, every polite form, たい, the provisional ば, the " +
                        "imperative, the potential, and the volitional.",
                ),
                PrimerBlock.Sub("The fusion system"),
                PrimerBlock.Line(
                    "In this system, rather than modifying the last kana and adding an ending, the " +
                        "form-specific ending fuses with the last kana. How they fuse depends on the " +
                        "specific last kana of the plain form, and must be memorized. This table shows " +
                        "all fusions for all nine possible う-row kana, for the て-form and the past " +
                        "form. 行[い]く is the one exception, and is covered below.",
                ),
                PrimerBlock.Table(
                    header = listOf("plain", "て-form", "past"),
                    rows = listOf(
                        listOf("う · つ · る", "って", "った"),
                        listOf("ぬ · ぶ · む", "んで", "んだ"),
                        listOf("く", "いて", "いた"),
                        listOf("ぐ", "いで", "いだ"),
                        listOf("す", "して", "した"),
                    ),
                ),
                PrimerBlock.Table(
                    header = emptyList(),
                    rows = listOf(
                        listOf("買[か]う", "買[か]って", "買[か]った"),
                        listOf("待[ま]つ", "待[ま]って", "待[ま]った"),
                        listOf("取[と]る", "取[と]って", "取[と]った"),
                        listOf("死[し]ぬ", "死[し]んで", "死[し]んだ"),
                        listOf("遊[あそ]ぶ", "遊[あそ]んで", "遊[あそ]んだ"),
                        listOf("読[よ]む", "読[よ]んで", "読[よ]んだ"),
                        listOf("書[か]く", "書[か]いて", "書[か]いた"),
                        listOf("泳[およ]ぐ", "泳[およ]いで", "泳[およ]いだ"),
                        listOf("話[はな]す", "話[はな]して", "話[はな]した"),
                    ),
                ),
            ),
        ),

        PrimerSection(
            "Irregular verbs",
            listOf(
                PrimerBlock.Line("There are only two verbs in Japanese that are irregular throughout:"),
                PrimerBlock.Bullet(
                    "する (to do) — し, さ and すれ all appear: しない, して, させる, すれば",
                ),
                PrimerBlock.Bullet("来[く]る (to come) — the reading itself changes: こない, きた, きて"),
                PrimerBlock.Line("Three more are regular verbs with exceptions worth knowing:"),
                PrimerBlock.Bullet(
                    "行[い]く — godan in every way, except that its て-form and past are 行[い]って and 行[い]った, " +
                        "not 行[い]いて",
                ),
                PrimerBlock.Bullet(
                    "ある — its negative is ない, not あらない. It also has no imperative, potential, " +
                        "passive or causative",
                ),
                PrimerBlock.Bullet("いる — already a state, so it is not put into the progressive ている"),
            ),
        ),

        PrimerSection(
            "Adjectives",
            listOf(
                PrimerBlock.Line(
                    "Japanese adjectives conjugate in two different ways, plus a single irregular " +
                        "adjective:",
                ),
                PrimerBlock.Bullet(
                    "い-adjectives behave much like verbs. The final い is replaced by the ending: " +
                        "高[たか]い → 高[たか]くない, 高[たか]かった.",
                ),
                PrimerBlock.Bullet(
                    "な-adjectives do not conjugate at all. The だ after them does the work: " +
                        "便[べん]利[り]だ → 便[べん]利[り]じゃない, 便[べん]利[り]だった. They are named for the form they take before a " +
                        "noun, 便[べん]利[り]な人[ひと].",
                ),
                PrimerBlock.Bullet("いい switches to よ- the moment it conjugates: よくない, よかった."),
            ),
        ),

        PrimerSection(
            "Forms built on other forms",
            listOf(
                PrimerBlock.Line(
                    "Some forms do not stop after one change. They produce a whole new word, which " +
                        "then conjugates by the rules above as though it had always been that kind " +
                        "of word.",
                ),
                PrimerBlock.Step("書[か]く", "書[か]ける", "potential"),
                PrimerBlock.Step("書[か]く", "書[か]かれる", "passive"),
                PrimerBlock.Step("書[か]く", "書[か]かせる", "causative"),
                PrimerBlock.Step("書[か]く", "書[か]いている", "progressive"),
                PrimerBlock.Line(
                    "Every one of those ends in -える or -いる, which means each one is now an ichidan " +
                        "verb. So 書[か]ける goes to 書[か]けない and 書[か]けた exactly the way 食[た]べる goes to " +
                        "食[た]べない and 食[た]べた. The same is true of the ichidan and irregular versions: " +
                        "食[た]べられる, させる and 来[こ]させる are all ichidan verbs too.",
                ),
                PrimerBlock.Step("書[か]く", "書[か]きたい", "desire"),
                PrimerBlock.Line(
                    "たい is not a verb at all but an い-adjective, so it behaves like 高[たか]い: " +
                        "書[か]きたくない, 書[か]きたかった.",
                ),
                PrimerBlock.Line(
                    "This is why the two systems are enough for the whole reference. A long form is " +
                        "a short form with another short form applied to it, and the second step " +
                        "follows rules you already know.",
                ),
            ),
        ),

        PrimerSection(
            "What comes next",
            listOf(
                PrimerBlock.Line(
                    "Every form in this reference is one of these changes plus an ending. Once the " +
                        "row shifts and the て-table are mastered, the rest is mostly vocabulary.",
                ),
            ),
        ),
    )
}
