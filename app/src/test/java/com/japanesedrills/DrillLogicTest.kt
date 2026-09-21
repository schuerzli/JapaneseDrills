package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
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
            File(assets, "lessons.json").readText(),
        )
    }

    private fun forms(word: String, conjugation: String): List<String> =
        data.words.first { it.key == word }.conjugations.getValue(conjugation).forms.map(Furigana::toKanji)

    private fun kana(word: String, conjugation: String): List<String> =
        data.words.first { it.key == word }.conjugations.getValue(conjugation).forms.map(Furigana::toKana)

    @Test
    fun loadsAllWords() {
        assertEquals(1000, data.words.size)
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
        assertEquals(1000, checked)
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

    /** Adjectives join the て form: the lesson on the connector is about them too. */
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
        val options = QuizOptions().with("common", true)
        val pool = engine.buildPool(options)
        assertTrue(pool.size > 0)
        repeat(100) {
            assertTrue("common" in engine.nextQuestion(pool)!!.word.tags)
        }
    }

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
                assertEquals(where, word.dictionary, solution.steps.first().from)
                assertEquals(where, conjugation.forms, solution.steps.last().to)
                solution.steps.zipWithNext { a, b -> assertTrue(where, b.from in a.to) }
            }
        }
    }

    @Test
    fun explainsStepsInOrder() {
        val word = data.words.first { it.key == "聞く" }
        val steps = Explanations.solution(word, "causative passive past negative").steps
        assertEquals(listOf("Causative", "Causative passive", "Past negative"), steps.map { it.label })
        assertEquals(listOf("聞[き]かせる"), steps[0].to)
        assertEquals(
            "Change the final kana from the う-row to the あ-row and add せる.",
            (steps[0].rule.single() as RichPart.Text).text,
        )
        assertEquals(RichPart.Text(" conjugates like an ichidan verb: "), steps[2].rule[1])
        assertEquals(RichPart.Text("drop the final る and add なかった."), steps[2].rule[2])

        val kau = data.words.first { it.key == "買う" }
        val negative = Explanations.solution(kau, "negative").steps.single()
        assertEquals(
            "Change the final kana from the う-row to the あ-row and add ない. う becomes わ, not あ.",
            (negative.rule.single() as RichPart.Text).text,
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

    @Test
    fun parsesFurigana() {
        assertEquals("ゆうめいだ", Furigana.toKana("有[ゆう]名[めい]だ"))
        assertEquals("有名だ", Furigana.toKanji("有[ゆう]名[めい]だ"))
        assertEquals(
            listOf(RubySegment("上", "あ"), RubySegment("げる", null)),
            Furigana.segments("上[あ]げる"),
        )
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
            listOf(RichPart.Jp("a"), RichPart.Text(", "), RichPart.Jp("b"), RichPart.Text(" or "), RichPart.Jp("c")),
            Prompts.wordList(listOf("a", "b", "c")),
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
     * The N4-N1 chips used to match nothing, because every word carried the same `n5` tag.
     * Each level filter must select a usable set, and must select only that level.
     */
    @Test
    fun everyLevelFilterSelectsWords() {
        val engine = QuizEngine(data)
        for (level in listOf("n5", "n4", "n3", "n2")) {
            val words = data.words.filter { level in it.tags }
            assertTrue("$level has ${words.size} words", words.size >= 150)

            val pool = engine.buildPool(QuizOptions().with(level, true))
            assertTrue(level, pool.size > 0)
            repeat(50) { assertTrue(level, level in engine.nextQuestion(pool)!!.word.tags) }
        }
        // A word belongs to at most one level.
        for (word in data.words) {
            assertTrue(word.key, word.tags.count { it in setOf("n5", "n4", "n3", "n2") } <= 1)
        }
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
