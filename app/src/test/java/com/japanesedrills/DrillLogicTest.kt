package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.quiz.ChangeShape
import com.japanesedrills.quiz.ConjugationIntro
import com.japanesedrills.quiz.ConjugationIntroBlock
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.FusionColumn
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.Mark
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.WordSets
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.Question
import com.japanesedrills.quiz.QuestionPool
import com.japanesedrills.quiz.RomajiConverter
import com.japanesedrills.quiz.RubySegment
import java.io.File
import kotlin.random.Random
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillLogicTest {

    private val data: DrillData by lazy {
        val assets = File("src/main/assets")
        DrillData.fromJson(
            File(assets, "words.json").readText(),
            File(assets, "rules.json").readText(),
            File(assets, "steps.json").readText(),
        )
    }

    private fun forms(word: String, conjugation: String): List<String> =
        data.words.first { it.key == word }.conjugations.getValue(conjugation).forms.map(Furigana::toKanji)

    private fun kana(word: String, conjugation: String): List<String> =
        data.words.first { it.key == word }.conjugations.getValue(conjugation).forms.map(Furigana::toKana)

    /**
     * A full merge fills the list to a thousand; curation run on its own can only remove, so
     * this is a floor against losing words by accident rather than an exact count.
     */
    @Test
    fun loadsAllWords() {
        assertTrue("only ${data.words.size} words", data.words.size >= 950)
        // Each word must appear once; the source list used to hold 静な/静かな and 行う/行なう twice.
        assertEquals(data.words.size, data.words.map { it.dictionary }.toSet().size)
    }

    /**
     * words.json carries a `reading` column that nothing parses; it is kept purely as a
     * second source for this check, which catches furigana that drops or invents kana.
     */
    @Test
    fun furiganaMatchesTheDeclaredReading() {
        val json = JSONObject(File("src/main/assets/words.json").readText())
        var checked = 0
        for (key in json.keys()) {
            val entry = json.getJSONObject(key)
            val dictionary = entry.getString("dictionary")
            assertEquals(key, entry.getString("reading"), Furigana.toKana(dictionary))
            // The written form must survive too: 支[しはら]う would silently render as 支う.
            val written = Furigana.toKanji(dictionary)
            assertTrue("$key -> $written", written.length >= key.length - 1)
            checked++
        }
        assertEquals(data.words.size, checked)
    }

    @Test
    fun everyWordCanBeAsked() {
        val dead = data.words.filter { word ->
            word.conjugations.filterKeys { it != DrillData.DICTIONARY }.all { it.value.forms.isEmpty() }
        }
        assertEquals(emptyList<String>(), dead.map { "${it.key} (${it.group})" })
    }

    @Test
    fun conjugatesRegularVerbs() {
        assertEquals(listOf("上げない"), forms("上げる", "negative"))
        assertEquals(listOf("なった"), forms("なる", "past"))
        assertEquals(listOf("なって"), forms("なる", "te-form"))
        assertEquals(listOf("一致しません"), forms("一致する", "polite negative"))
    }

    @Test
    fun conjugatesIrregularWords() {
        assertEquals(listOf("来ない"), forms("来る", "negative"))
        assertEquals(listOf("行って"), forms("行く", "te-form"))
        assertEquals(listOf("ない"), forms("ある", "negative"))
        assertEquals(listOf("良かった"), forms("いい", "past"))
        assertEquals(listOf("高くない"), forms("高い", "negative"))
    }

    /** Adjectives join the て-form: the step on the connector is about them too. */
    @Test
    fun adjectivesHaveATeForm() {
        assertEquals(listOf("高くて"), forms("高い", "te-form"))
        assertEquals(listOf("良くて"), forms("いい", "te-form"))
        assertEquals(listOf("便利で"), forms("便利な", "te-form"))
        assertEquals(listOf("高くなくて"), forms("高い", "te-form negative"))
        assertEquals(listOf("良くなくて"), forms("いい", "te-form negative"))
        assertEquals(listOf("便利ではなくて", "便利じゃなくて"), forms("便利な", "te-form negative"))
    }

    /** いたい is everyday Japanese; only いる's progressive is missing on purpose. */
    @Test
    fun iruHasTheWholeDesireFamily() {
        fun desire(group: String) = data.ownForms.getValue(group).filter { it.startsWith("desire") }.toSet()
        assertEquals(desire("ichidan"), desire("iru"))
        assertEquals(listOf("いたい"), forms("いる", "desire"))
        assertTrue(data.ownForms.getValue("iru").none { it.startsWith("progressive") })
    }

    /** 来る lists every desire form itself, so it has to list the same ones as the others. */
    @Test
    fun kuruHasTheWholeDesireFamily() {
        fun desire(group: String) = data.ownForms.getValue(group).filter { it.startsWith("desire") }.toSet()
        assertEquals(desire("godan"), desire("kuru"))
        assertEquals(listOf("来たかった"), forms("来る", "desire past"))
        assertEquals(listOf("来たくないです"), forms("来る", "desire polite negative"))
    }

    @Test
    fun regroupedVerbsConjugateCorrectly() {
        assertEquals(listOf("見ない"), forms("見る", "negative"))
        assertEquals(listOf("出来た"), forms("出来る", "past"))
        assertEquals(listOf("考えて"), forms("考える", "te-form"))
        assertEquals(listOf("受け取らない"), forms("受け取る", "negative"))
        assertEquals(listOf("要しない"), forms("要する", "negative"))
        assertEquals("ようしない", data.words.first { it.key == "要する" }
            .conjugations.getValue("negative").forms.single().let(Furigana::toKana))
    }

    @Test
    fun commonFilterSelectsHundredVerbs() {
        val common = data.words.filter { "common" in it.tags }
        assertEquals(100, common.size)
        assertTrue(common.none { it.group.endsWith("adjective") || it.group == "ii" })

        val engine = QuizEngine(data, Random(5))
        val pool = engine.buildPool(onlySets("common"))
        assertTrue(pool.size > 0)
        repeat(100) {
            assertTrue("common" in engine.nextQuestion(pool)!!.word.tags)
        }
    }

    /**
     * Free practice over the sets switched on and nothing else, with the whole grid on, so a
     * test about which words are drawn is not answered by a class being switched off.
     */
    private fun onlySets(vararg ids: String) = QuizOptions()
        .select(QuizOptions.FORM_KEYS, QuizOptions.GROUP_KEYS)
        .copy(allWords = false, sets = ids.toSet())

    @Test
    fun fixedRulesProduceCorrectForms() {
        assertEquals(listOf("聞いたら"), forms("聞く", "conditional"))
        assertEquals(listOf("聞かなければ"), forms("聞く", "provisional negative"))
        assertEquals(listOf("いたら"), forms("いる", "conditional"))
        assertEquals(listOf("いさせる"), forms("いる", "causative"))
        assertEquals(listOf("聞かせられなかった", "聞かされなかった"), forms("聞く", "causative passive past negative"))
    }

    @Test
    fun acceptsCommonAlternativeForms() {
        // Contracted godan causative passive, which verbs ending in す do not have.
        assertEquals(listOf("聞かせられる", "聞かされる"), forms("聞く", "causative passive"))
        assertEquals(listOf("行かせられる", "行かされる"), forms("行く", "causative passive"))
        assertEquals(listOf("話させられる"), forms("話す", "causative passive"))
        // The written imperative of する.
        assertEquals(listOf("しろ", "せよ"), forms("する", "imperative"))
        assertEquals(listOf("記憶しろ", "記憶せよ"), forms("記憶する", "imperative"))
        // Casual ら抜き potential, now accepted in every potential form.
        assertEquals(listOf("食べられる", "食べれる"), forms("食べる", "potential"))
        assertEquals(listOf("食べられない", "食べれない"), forms("食べる", "potential negative"))
        assertEquals(listOf("食べられます", "食べれます"), forms("食べる", "polite potential"))
        assertEquals(listOf("食べられません", "食べれません"), forms("食べる", "polite potential negative"))

        val kiku = data.words.first { it.key == "聞く" }
        val shortForm = Explanations.solution(kiku, "causative passive").steps.last()
        assertTrue(
            (shortForm.rule.last() as RichPart.Text).text
                .contains("shorter form: add される to the あ-row kana instead"),
        )
        val hanasu = data.words.first { it.key == "話す" }
        val noShortForm = Explanations.solution(hanasu, "causative passive").steps.last()
        assertFalse((noShortForm.rule.last() as RichPart.Text).text.contains("shorter form"))
    }

    @Test
    fun rareAruFormsAreDisabled() {
        val aru = data.words.first { it.key == "ある" }
        for (key in listOf("potential", "passive", "causative", "causative passive", "imperative")) {
            assertFalse(key, aru.conjugations.containsKey(key))
        }
        // The everyday forms still work.
        assertEquals(listOf("ない"), forms("ある", "negative"))
        assertEquals(listOf("あれば"), forms("ある", "provisional"))
        assertEquals(listOf("あるな"), forms("ある", "imperative negative"))

        val engine = QuizEngine(data)
        val options = QuizOptions().with("potential", true).with("passive", true).with("causative", true)
        val pool = engine.buildPool(options)
        repeat(300) {
            val question = engine.nextQuestion(pool)!!
            if (question.word.key == "ある") {
                assertTrue(question.transformation.tags.none { it in setOf("potential", "passive", "causative") })
            }
        }
    }

    @Test
    fun everyFormHasAnExplanation() {
        for (word in data.words) {
            for ((key, conjugation) in word.conjugations) {
                if (conjugation.forms.isEmpty()) continue
                val solution = Explanations.solution(word, key)
                val where = "${word.key} / $key"
                assertFalse("fallback text for $where", solution.usedFallback)
                if (key == "dictionary") {
                    assertTrue(where, solution.steps.isEmpty())
                    continue
                }
                assertTrue(where, solution.steps.isNotEmpty())
                assertEquals(where, listOf(word.dictionary), solution.steps.first().from)
                assertEquals(where, conjugation.forms, solution.steps.last().to)
                // Each step builds on everything the one before produced.
                solution.steps.zipWithNext { a, b -> assertEquals(where, a.to, b.from) }
            }
        }
    }

    @Test
    fun explainsStepsInOrder() {
        val word = data.words.first { it.key == "聞く" }
        val steps = Explanations.solution(word, "causative passive past negative").steps
        assertEquals(listOf("Causative", "Causative passive", "Negative", "Past negative"), steps.map { it.label })
        assertEquals(listOf("聞[き]かせる"), steps[0].to)
        assertEquals(
            "Change the last kana from the う-row to the あ-row and add せる.",
            (steps[0].rule.single() as RichPart.Text).text,
        )
        assertEquals(RichPart.Text(" conjugates like an ichidan verb: "), steps[2].rule[1])
        assertEquals(RichPart.Text("drop the last る and add ない."), steps[2].rule[2])
        assertEquals(
            "The negative ends in ない, which conjugates like an い-adjective: replace the last い with かった.",
            (steps[3].rule.single() as RichPart.Text).text,
        )

        // A verb's polite forms are built from ます, one change at a time.
        val kaku = data.words.first { it.key == "書く" }
        val polite = Explanations.solution(kaku, "polite past negative").steps
        assertEquals(listOf("Polite", "Polite negative", "Polite past negative"), polite.map { it.label })
        assertEquals(listOf("書[か]きます", "書[か]きません", "書[か]きませんでした"), polite.map { it.to.single() })
        assertEquals(listOf("Replace ます with ません.", "Add でした."), polite.drop(1).map { (it.rule.single() as RichPart.Text).text })

        val kau = data.words.first { it.key == "買う" }
        val negative = Explanations.solution(kau, "negative").steps.single()
        assertEquals(
            "Change the last kana from the う-row to the あ-row and add ない. う becomes わ, not あ.",
            (negative.rule.single() as RichPart.Text).text,
        )
    }

    @Test
    fun marksTheStepsThatUseAGodanFusion() {
        fun changes(key: String, target: String) =
            Explanations.solution(data.words.first { it.key == key }, target).steps.map { it.fusion }
        assertEquals(listOf(FusionColumn.TE_FORM), changes("書く", "te-form"))
        assertEquals(listOf(FusionColumn.PAST, null), changes("書く", "conditional"))
        assertEquals(listOf(FusionColumn.TE_FORM), changes("書く", "progressive"))
        assertEquals(listOf(null), changes("書く", "negative"))
        // 行く is the exception to the table, and 食べる never uses it.
        assertEquals(listOf(null), changes("行く", "te-form"))
        assertEquals(listOf(null, null), changes("食べる", "conditional"))
    }

    @Test
    fun labelsIrregularChangesInsteadOfExplainingThem() {
        fun rules(key: String, target: String) =
            Explanations.solution(data.words.first { it.key == key }, target).steps.map { it.rule }
        val irregular = listOf(RichPart.Tag("Irregular"))
        val here = listOf(RichPart.Tag("Irregular in this case"))
        assertEquals(listOf(irregular), rules("来る", "negative"))
        assertEquals(listOf(irregular), rules("する", "causative"))
        assertEquals(listOf(here), rules("行く", "te-form"))
        assertEquals(listOf(here), rules("ある", "negative"))
        // What follows the irregular form is ordinary again, and explained.
        val (_, past) = rules("来る", "past negative")
        assertTrue((past.single() as RichPart.Text).text.startsWith("The negative ends in ない"))
        // 行く is irregular only in the forms built on its て-form.
        assertTrue(rules("行く", "negative").single().single() is RichPart.Text)
    }

    @Test
    fun carriesEachSpellingThroughItsOwnSteps() {
        val word = data.words.first { it.key == "有名な" }
        val (negative, past) = Explanations.solution(word, "past negative").steps
        assertEquals(listOf("有[ゆう]名[めい]ではない", "有[ゆう]名[めい]じゃない"), negative.to)
        // Each past negative starts from its own negative, not from the first one listed.
        val lines = Prompts.change(past.from, past.to, past.shape).map { line ->
            line.joinToString("") {
                when (it) {
                    is RichPart.Jp -> it.word
                    is RichPart.Marked -> it.text
                    is RichPart.Text -> it.text
                    is RichPart.Tag -> it.text
                }
            }
        }
        assertEquals(
            listOf(
                "有[ゆう]名[めい]ではない  →  有[ゆう]名[めい]ではなかった",
                "有[ゆう]名[めい]じゃない  →  有[ゆう]名[めい]じゃなかった",
            ),
            lines,
        )
    }

    @Test
    fun naAdjectivesEndingInNaStillConjugate() {
        assertEquals(listOf("元気です"), forms("元気な", "polite"))
        assertEquals(listOf("有名ではない", "有名じゃない"), forms("有名な", "negative"))
        // 頑な (かたくな) keeps its な: the stem is かたくな, not かたく.
        assertEquals(listOf("頑なです"), forms("頑な", "polite"))
        assertEquals("かたくなだ", data.words.first { it.key == "頑な" }.dictionary.let(Furigana::toKana))
    }

    @Test
    fun regroupedAdjectivesConjugateCorrectly() {
        assertEquals(listOf("暑くない"), forms("暑い", "negative"))
        assertEquals(listOf("暖かかった"), forms("暖かい", "past"))
        assertEquals(listOf("幸いです"), forms("幸い", "polite"))
        // 嫌い ends in い but is a な-adjective: 嫌くない is not a word.
        assertEquals(listOf("嫌いではない", "嫌いじゃない"), forms("嫌い", "negative"))
        assertEquals(listOf("黄色くない"), forms("黄色い", "negative"))
        assertEquals(listOf("きいろくない"), kana("黄色い", "negative"))
    }

    @Test
    fun repairedFuriganaKeepsBothSpellings() {
        // 支[しはら]う used to render as 支う, so the real kanji answer was rejected.
        assertEquals(listOf("支払わない"), forms("支払う", "negative"))
        assertEquals(listOf("四角くない"), forms("四角い", "negative"))
        assertEquals(listOf("大人しくない"), forms("大人しい", "negative"))
        assertEquals(listOf("美味しくない"), forms("美味しい", "negative"))
        assertEquals(listOf("しはらわない"), kana("支払う", "negative"))
        assertEquals(listOf("おいしくない"), kana("美味しい", "negative"))
    }

    @Test
    fun iruAcceptsTheSameCasualFormsAsOtherIchidanVerbs() {
        assertEquals(listOf("いられる", "いれる"), forms("いる", "potential"))
        assertEquals(listOf("いられない", "いれない"), forms("いる", "potential negative"))
        assertEquals(listOf("いられます", "いれます"), forms("いる", "polite potential"))
        assertEquals(listOf("いられません", "いれません"), forms("いる", "polite potential negative"))
        // いろ is everyday, いよ literary, so いろ must be shown first.
        assertEquals(listOf("いろ", "いよ"), forms("いる", "imperative"))
    }

    @Test
    fun buildsTransformationsBothWays() {
        val pairs = data.transformations.filter { !it.isTrick }.map { it.from to it.to }.toSet()
        assertTrue("dictionary" to "negative" in pairs)
        assertTrue("negative" to "dictionary" in pairs)
        assertTrue("polite negative" to "negative" in pairs)

        val toNegative = data.transformations.first { it.from == "dictionary" && it.to == "negative" }
        assertEquals("negative", toNegative.phrase)
        assertEquals("negative", toNegative.type)
        assertEquals(listOf("plain"), toNegative.fromTags)
        assertEquals(listOf("plain", "negative"), toNegative.toTags)

        val toPlain = data.transformations.first { it.from == "polite" && it.to == "dictionary" }
        assertEquals("plain", toPlain.phrase)
        assertEquals("politeness", toPlain.type)
        assertTrue(data.transformations.any { it.isTrick })
    }

    @Test
    fun defaultPoolOnlyUsesEnabledForms() {
        val engine = QuizEngine(data, Random(1))
        val options = QuizOptions()
        val pool = engine.buildPool(options)
        assertTrue(pool.size > 0)

        repeat(200) {
            val q = engine.nextQuestion(pool)!!
            val t = q.transformation
            assertTrue(t.tags.none { it == "polite" || it == "te-form" || it == "potential" })
            assertFalse(q.word.group == "i-adjective" || q.word.group == "na-adjective")
            assertTrue(q.answers.isNotEmpty())
            assertTrue(q.isCorrect(Furigana.toKana(q.answers.first())))
            assertTrue(q.isCorrect(Furigana.toKanji(q.answers.first())))
        }
    }

    /**
     * The grid's squares: a form of one word class, switched off on its own. The rest of
     * that form, and the rest of that class, carry on being asked.
     */
    @Test
    fun aSwitchedOffSquareOnlyRemovesItsOwnPairing() {
        val engine = QuizEngine(data, Random(11))
        val on = QuizOptions()
        val off = on.withSquare("past", "ichidan", false)
        assertTrue(engine.buildPool(off).size < engine.buildPool(on).size)

        val pool = engine.buildPool(off)
        var godanPast = 0
        var ichidanOther = 0
        repeat(400) {
            val q = engine.nextQuestion(pool)!!
            val column = QuizOptions.columnOf(q.word.group)
            assertFalse("past of an ichidan verb", column == "ichidan" && "past" in q.transformation.tags)
            if (column == "godan" && "past" in q.transformation.tags) godanPast++
            if (column == "ichidan") ichidanOther++
        }
        assertTrue("the rest of the past is still asked", godanPast > 0)
        assertTrue("the rest of the ichidan verbs are still asked", ichidanOther > 0)
    }

    /** A preset states a whole selection, so it cannot inherit yesterday's holes. */
    @Test
    fun aPresetFillsTheGridIn() {
        val punched = QuizOptions().withSquare("past", "godan", false)
        val preset = punched.withPreset(PracticePreset.Everything, emptySet(), emptySet())
        assertTrue(preset.offSquares.isEmpty())
        assertTrue(preset.asksSquare("past", "godan"))
    }

    /** The grid draws a column for every class its words fall into, and no others. */
    @Test
    fun theGridHasAColumnForEveryClassInThePool() {
        val engine = QuizEngine(data, Random(12))
        assertEquals(
            data.words.mapTo(HashSet()) { QuizOptions.columnOf(it.group) },
            engine.columnsFor(QuizOptions()),
        )
        val adjectives = data.words.filter { it.group == "na-adjective" }.mapTo(HashSet()) { it.key }
        assertEquals(setOf("na-adjective"), engine.columnsFor(QuizOptions(wordKeys = adjectives)))
    }

    /** A word class the grid has no column for could never be practised. */
    @Test
    fun everyWordClassBelongsToAColumn() {
        val columns = QuizOptions.COLUMNS.flatMap { it.groups }
        assertEquals("a group in two columns", columns.size, columns.toSet().size)
        for (group in data.words.mapTo(HashSet()) { it.group }) {
            assertTrue("no column holds $group", group in columns)
        }
    }

    @Test
    fun questionFocusRestrictsType() {
        val engine = QuizEngine(data, Random(2))
        val pool = engine.buildPool(QuizOptions().with("polite", true).copy(questionFocus = "politeness"))
        repeat(100) {
            assertEquals("politeness", engine.nextQuestion(pool)!!.transformation.type)
        }
    }

    @Test
    fun tetakeiFocusOnlyAsksGodanTeTaForms() {
        val engine = QuizEngine(data, Random(3))
        val options = QuizOptions().with("te-form", true).copy(questionFocus = QuizOptions.FOCUS_TETAKEI)
        val pool = engine.buildPool(options)
        assertTrue(pool.size > 0)
        repeat(100) {
            val q = engine.nextQuestion(pool)!!
            assertEquals("godan", q.word.group)
        }
    }

    @Test
    fun disablingPlainAndPoliteEmptiesPool() {
        val engine = QuizEngine(data)
        val pool = engine.buildPool(QuizOptions().with("plain", false))
        assertEquals(0, pool.size)
    }

    @Test
    fun kanaModeHidesKanji() {
        val engine = QuizEngine(data, Random(4))
        val pool = engine.buildPool(QuizOptions().with(QuizOptions.KANA, true))
        repeat(50) {
            val q = engine.nextQuestion(pool)!!
            assertFalse(Furigana.hasReading(q.givenDisplay(kana = true)))
            assertTrue(q.answersDisplay(kana = true).none(Furigana::hasReading))
            assertFalse(Furigana.hasReading(q.dictionaryDisplay(kana = true)))
        }
    }

    @Test
    fun convertsRomaji() {
        assertEquals("たべない", RomajiConverter.convert("tabenai"))
        assertEquals("よみませn", RomajiConverter.convert("yomimasen"))
        assertEquals("よみません", RomajiConverter.finish("yomimasen"))
        assertEquals("いって", RomajiConverter.convert("itte"))
        assertEquals("しゃしん", RomajiConverter.convert("shashinn"))
        assertEquals("かんじ", RomajiConverter.convert("kanji"))
        assertEquals("ちょっと", RomajiConverter.convert("chotto"))
        assertEquals("つかう", RomajiConverter.convert("tsukau"))
        assertEquals("おおきい", RomajiConverter.convert("ookii"))
        assertEquals("k", RomajiConverter.convert("k"))
        assertEquals("食べ", RomajiConverter.convert("食be"))
    }

    /** The same word in two spellings was drilled twice: かける and 掛ける, 下りる and 降りる. */
    @Test
    fun noWordIsListedTwiceUnderTwoSpellings() {
        val twice = data.words
            .groupBy { Triple(Furigana.toKana(it.dictionary), it.group, it.meaning.substringBefore(',')) }
            .values.filter { it.size > 1 }
            .map { same -> same.joinToString(" / ") { it.key } }
        assertEquals(emptyList<String>(), twice)
    }

    /**
     * JMdict glosses a する verb's noun; beside "to eat", "travel, trip" did not read as a verb.
     * A new one needs its meaning in merge.py's SURU_GLOSSES.
     */
    @Test
    fun everySuruVerbIsGlossedAsAVerb() {
        val nouny = data.words
            .filter { it.group == "suru" && !it.meaning.startsWith("to ") }
            .map { it.key }
        assertEquals(emptyList<String>(), nouny)
    }

    @Test
    fun parsesFurigana() {
        assertEquals("ゆうめいだ", Furigana.toKana("有[ゆう]名[めい]だ"))
        assertEquals("有名だ", Furigana.toKanji("有[ゆう]名[めい]だ"))
        assertEquals(
            listOf(RubySegment("上", "あ"), RubySegment("げる", null)),
            Furigana.segments("上[あ]げる"),
        )
        // A reading shared by several kanji, as sentences need for 今日.
        assertEquals("きょうは", Furigana.toKana("{今日}[きょう]は"))
        assertEquals("今日は", Furigana.toKanji("{今日}[きょう]は"))
        assertEquals(
            listOf(RubySegment("今日", "きょう"), RubySegment("は", null), RubySegment("雨", "あめ")),
            Furigana.segments("{今日}[きょう]は雨[あめ]"),
        )
    }

    /**
     * Readings are shown over every kanji in the app, so every text that reaches the screen
     * has to carry them. A kanji with no reading after it is one the learner cannot read.
     */
    @Test
    fun everyKanjiShownHasAReading() {
        val bare = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff々](?!\\[)")
        fun unread(text: String) = bare.containsMatchIn(text.replace(Regex("\\{[^}]*\\}\\[[^\\]]*\\]"), ""))

        val texts = buildList {
            for (note in Grammar.NOTES + Grammar.CLASS_NOTES) {
                add(note.title); add(note.summary); addAll(note.notes)
            }
            for (section in ConjugationIntro.SECTIONS) {
                add(section.title)
                for (block in section.blocks) when (block) {
                    is ConjugationIntroBlock.Line -> add(block.text)
                    is ConjugationIntroBlock.Bullet -> add(block.text)
                    is ConjugationIntroBlock.Sub -> add(block.title)
                    is ConjugationIntroBlock.Step -> { add(block.from); add(block.to); add(block.note) }
                    is ConjugationIntroBlock.Table -> { addAll(block.header); block.rows.forEach(::addAll) }
                    is ConjugationIntroBlock.Legend -> Unit
                }
            }
            addAll(QuizOptions.ALL.map { it.label })
            addAll(QuizOptions.COLUMNS.map { it.label })
            addAll(WordSets.BUILT_IN.map { it.label })
            for (step in data.learnPath.steps) {
                add(step.title); add(step.subtitle); add(step.chapter)
            }
            addAll(data.words.map { it.sentenceJp })
        }
        assertEquals("kanji without a reading", emptyList<String>(), texts.filter(::unread))
    }

    /**
     * A Conjugation Intro example whose marks do not close would show its brackets as text.
     * Unmarked, every example reads as plain Japanese, and each change starts from a marked last kana.
     */
    @Test
    fun conjugationIntroExamplesAreMarkedUpCleanly() {
        val examples = ConjugationIntro.SECTIONS.flatMap { it.blocks }.flatMap { block ->
            when (block) {
                is ConjugationIntroBlock.Step -> listOf(block.from, block.to)
                is ConjugationIntroBlock.Table -> if (block.marked) block.rows.flatten() else emptyList()
                else -> emptyList()
            }
        }
        val leftover = examples.filter { RichPart.unmarked(it).any { c -> c in "()〈〉+" } }
        assertEquals(emptyList<String>(), leftover)
        assertEquals(
            listOf(RichPart.Jp("書[か]"), RichPart.Marked("け", Mark.LastKana), RichPart.Marked("る", Mark.Ending)),
            RichPart.marked("書[か](け)+る"),
        )
        assertEquals(
            listOf(RichPart.Jp("書[か]"), RichPart.Marked("いて", Mark.Fused), RichPart.Marked("いる", Mark.Ending)),
            RichPart.marked("書[か]〈いて〉+いる"),
        )
    }

    /**
     * The Conjugation Intro is written by hand, so nothing else stops it teaching a form the
     * drill would mark wrong: every example that starts from a word in the list has to end
     * on one of that word's real conjugations. It is what would catch 食べば for 食べれば.
     */
    @Test
    fun conjugationIntroExamplesAreRealConjugations() {
        val changes = ConjugationIntro.SECTIONS.flatMap { it.blocks }.flatMap { block ->
            when (block) {
                is ConjugationIntroBlock.Step -> listOf(block.from to block.to)
                is ConjugationIntroBlock.Table ->
                    if (block.marked) block.rows.flatMap { row -> row.drop(1).map { row.first() to it } } else emptyList()
                else -> emptyList()
            }
        }
        var checked = 0
        val wrong = changes.filter { (from, to) ->
            val start = RichPart.unmarked(from)
            val candidates = data.words.filter { it.dictionary == start }
                .ifEmpty { data.words.filter { Furigana.toKana(it.dictionary) == Furigana.toKana(start) } }
            if (candidates.isEmpty()) return@filter false // a form built on a form, such as 書ける
            checked++
            val result = Furigana.toKana(RichPart.unmarked(to))
            candidates.none { word -> word.conjugations.values.any { c -> c.forms.any { Furigana.toKana(it) == result } } }
        }
        assertEquals(emptyList<Pair<String, String>>(), wrong)
        assertTrue("only $checked examples checked", checked >= 30)
    }

    /** Marks derived from a change agree with the ones the Conjugation Intro writes by hand. */
    @Test
    fun marksAChangeByWhatItDoesToTheLastKana() {
        fun marked(from: String, to: String, shape: ChangeShape) = ChangeShape.marked(from, to, shape).toList()
        assertEquals(
            listOf(RichPart.marked("書[か](く)"), RichPart.marked("書[か](か)+ない")),
            marked("書[か]く", "書[か]かない", ChangeShape.Shift),
        )
        assertEquals(
            listOf(RichPart.marked("食[た]べ(る)"), RichPart.marked("食[た]べ+れば")),
            marked("食[た]べる", "食[た]べれば", ChangeShape.Drop),
        )
        assertEquals(
            listOf(RichPart.marked("書[か](く)"), RichPart.marked("書[か]〈いて〉+いる")),
            marked("書[か]く", "書[か]いている", ChangeShape.Fuse(tail = "いる")),
        )
        // Nothing taken off, only an ending added.
        assertEquals(RichPart.marked("書[か]く+な"), marked("書[か]く", "書[か]くな", ChangeShape.Shift)[1])
        // Irregular: no last kana to point at.
        assertEquals(listOf(RichPart.Jp("来[く]る")), marked("来[く]る", "来[こ]ない", ChangeShape.None)[0])
    }

    @Test
    fun buildsPrompts() {
        assertEquals(
            listOf(RichPart.Text("change to "), RichPart.Text("negative", true)),
            Prompts.instruction("negative"),
        )
        assertEquals(
            listOf(RichPart.Text("change to "), RichPart.Text("past tense", true)),
            Prompts.instruction("past"),
        )
        assertEquals(
            listOf(
                RichPart.Text("change to "),
                RichPart.Text("negative", true),
                RichPart.Text(": "),
                RichPart.Jp("食べる"),
            ),
            Prompts.question("negative", "食べる"),
        )
        assertEquals(
            listOf(
                listOf(RichPart.Text("Correct: "), RichPart.Jp("a")),
                listOf(RichPart.Text("or "), RichPart.Jp("b")),
                listOf(RichPart.Text("or "), RichPart.Jp("c")),
            ),
            Prompts.alternatives(listOf("a", "b", "c"), lead = "Correct: "),
        )
    }

    @Test
    fun recognisesJapaneseText() {
        assertTrue(QuizEngine.isJapanese("食べない"))
        assertTrue(QuizEngine.isJapanese("ヴぁ"))
        assertFalse(QuizEngine.isJapanese("たべn"))
        assertFalse(QuizEngine.isJapanese(""))
        // The iteration mark 々 used to fall outside the accepted ranges.
        assertTrue(QuizEngine.isJapanese("華々しくない"))
    }

    /** Nothing is answerable if the validator rejects the answer the drill expects. */
    @Test
    fun everyAcceptedAnswerCountsAsJapanese() {
        for (word in data.words) {
            for ((key, conjugation) in word.conjugations) {
                for (form in conjugation.forms) {
                    val where = "${word.key} / $key"
                    assertTrue(where, QuizEngine.isJapanese(Furigana.toKanji(form)))
                    assertTrue(where, QuizEngine.isJapanese(Furigana.toKana(form)))
                }
            }
        }
    }

    /**
     * The N4-N1 lists used to match nothing, because every word carried the same `n5` tag.
     * Each list must select a usable set, and must select only that level.
     */
    @Test
    fun everyLevelSetSelectsWords() {
        val engine = QuizEngine(data)
        for (level in listOf("n5", "n4", "n3", "n2")) {
            val words = data.words.filter { level in it.tags }
            assertTrue("$level has ${words.size} words", words.size >= 150)

            val pool = engine.buildPool(onlySets(level))
            assertTrue(level, pool.size > 0)
            repeat(50) { assertTrue(level, level in engine.nextQuestion(pool)!!.word.tags) }
        }
        // A word belongs to at most one level.
        for (word in data.words) {
            assertTrue(word.key, word.tags.count { it in setOf("n5", "n4", "n3", "n2") } <= 1)
        }
    }

    /**
     * Sets add up rather than multiply: a word in two of them is still one word, so two
     * sets together ask exactly what each asks, and nothing twice.
     */
    @Test
    fun setsStackWithoutAskingAWordTwice() {
        val engine = QuizEngine(data)
        val common = engine.buildPool(onlySets("common")).size
        val n5 = engine.buildPool(onlySets("n5")).size
        val both = engine.buildPool(onlySets("common", "n5")).size
        val shared = data.words.count { "common" in it.tags && "n5" in it.tags && it.group !in WordSets.PARTIAL_GROUPS }
        assertTrue("the lists do overlap", shared > 0)
        assertTrue("a stacked set is not the sum", both < common + n5)
        assertTrue(both >= maxOf(common, n5))
    }

    /**
     * 行く, ある, いる and いい are held back from the lists and kept in one set of their own:
     * they have no column on the practice grid, so the set is what makes them switchable.
     */
    @Test
    fun theListsLeaveThePartiallyIrregularWordsToTheirOwnSet() {
        val partial = data.words.filter { it.group in WordSets.PARTIAL_GROUPS }
        assertEquals(WordSets.PARTIAL_GROUPS, partial.mapTo(HashSet()) { it.group })
        for (word in partial) {
            assertTrue(word.key, WordSets.holds(WordSets.PARTIAL, word))
            for (list in WordSets.IDS - WordSets.PARTIAL) {
                assertFalse("$list holds ${word.key}", WordSets.holds(list, word))
            }
        }
        val engine = QuizEngine(data)
        val words = HashSet<String>()
        val pool = engine.buildPool(onlySets(WordSets.PARTIAL))
        repeat(60) { words += engine.nextQuestion(pool)!!.word.key }
        assertEquals(partial.mapTo(HashSet()) { it.key }, words)
    }

    @Test
    fun everyOfferedOptionHasADefault() {
        for (item in QuizOptions.ALL) {
            assertTrue(item.key, item.key in QuizOptions.DEFAULT_FLAGS)
        }
        assertEquals(QuizOptions.ALL.size, QuizOptions.DEFAULT_FLAGS.size)
        // Turning nothing on must leave the pool as wide as it was before filters existed.
        val engine = QuizEngine(data)
        assertEquals(engine.buildPool(QuizOptions()).size, engine.buildPool(QuizOptions().with("common", false)).size)
    }
}

/** One question from [pool], drawn the way a session draws them. */
private fun QuizEngine.nextQuestion(pool: QuestionPool): Question? =
    buildQueue(pool, 1).firstOrNull()?.let(::questionFor)
