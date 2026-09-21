package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.Curriculum
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState
import com.japanesedrills.quiz.TransformationBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learn path's safety net. These are the checks that turn a bad edit to lessons.json —
 * or to words.json underneath it — into a build failure rather than a lesson that cannot
 * be played.
 */
class LearnPathTest {

    private val data: DrillData by lazy {
        val assets = File("src/main/assets")
        DrillData.fromJson(
            File(assets, "words.json").readText(),
            File(assets, "rules.json").readText(),
            File(assets, "lessons.json").readText(),
        )
    }

    private val curriculum: Curriculum get() = data.curriculum
    private val engine: QuizEngine by lazy { QuizEngine(data) }

    // Curriculum shape

    @Test
    fun everyPrerequisiteExists() {
        val ids = curriculum.lessons.map { it.id }.toSet()
        for (lesson in curriculum.lessons) {
            for (required in lesson.requires) {
                assertTrue("${lesson.id} requires unknown $required", required in ids)
            }
        }
    }

    @Test
    fun everyLessonIsReachableFromARoot() {
        val passed = HashSet<String>()
        // Repeatedly take whatever has become unlocked; anything left over is unreachable.
        var progressed = true
        while (progressed) {
            progressed = false
            for (lesson in curriculum.lessons) {
                if (lesson.id !in passed && curriculum.isUnlocked(lesson.id, passed)) {
                    passed += lesson.id
                    progressed = true
                }
            }
        }
        val unreachable = curriculum.lessons.map { it.id }.filterNot { it in passed }
        assertEquals("unreachable lessons", emptyList<String>(), unreachable)
    }

    @Test
    fun theFirstLessonNeedsNothing() {
        assertTrue(curriculum.lessons.any { it.requires.isEmpty() })
    }

    /**
     * A lesson nothing requires is a side road the path can be finished without. The
     * irregulars were three of those, which is how a learner could reach the end having
     * never been asked about ある.
     */
    @Test
    fun onlyTheLastLessonIsADeadEnd() {
        val required = curriculum.lessons.flatMap { it.requires }.toSet()
        val deadEnds = curriculum.lessons.map { it.id }.filterNot { it in required }
        assertEquals(listOf(curriculum.lessons.last().id), deadEnds)
    }

    /** The path groups consecutive lessons, so a chapter that came back would split in two. */
    @Test
    fun eachChapterIsOneUnbrokenRun() {
        val runs = curriculum.lessons.map { it.chapter }
            .fold(emptyList<String>()) { acc, chapter -> if (acc.lastOrNull() == chapter) acc else acc + chapter }
        assertEquals("a chapter appears in more than one place", runs.size, runs.toSet().size)
    }

    @Test
    fun lessonsAreInPathOrder() {
        val orders = curriculum.lessons.map { it.order }
        assertEquals(orders.sorted(), orders)
        assertEquals("orders must be distinct", orders.size, orders.toSet().size)
    }

    // Vocabulary

    @Test
    fun everyIntroducedWordExists() {
        for (lesson in curriculum.lessons) {
            for (key in lesson.newWords) {
                assertTrue(
                    "${lesson.id} introduces $key, which is not in words.json",
                    key in data.wordsByKey,
                )
            }
        }
    }

    @Test
    fun noWordIsIntroducedTwice() {
        val seen = HashMap<String, String>()
        for (lesson in curriculum.lessons) {
            for (key in lesson.newWords) {
                val first = seen.put(key, lesson.id)
                assertTrue("$key introduced by both $first and ${lesson.id}", first == null)
            }
        }
    }

    @Test
    fun formsAndWordsAccumulateAlongThePath() {
        // て form sits after plain/polite, negative and past, so it inherits all of them.
        val forms = curriculum.forms("te-form")
        assertTrue(forms.containsAll(listOf("plain", "polite", "negative", "past", "te-form")))
        // and it inherits every word introduced before it, without introducing any itself.
        assertTrue(curriculum["te-form"]!!.newWords.isEmpty())
        assertTrue(curriculum.words("te-form").size >= 24)
    }

    // Playability: the checks that matter most, because a lesson that cannot fill its
    // question count is only discoverable by playing it.

    @Test
    fun everyLessonHasEnoughQuestions() {
        for (lesson in curriculum.lessons) {
            val pool = engine.buildPool(curriculum.optionsFor(lesson, QuizOptions()))
            assertTrue(
                "${lesson.id} offers ${pool.size} questions but asks ${lesson.questions}",
                pool.size >= lesson.questions,
            )
        }
    }

    @Test
    fun everyLessonDrillsTheWordsItIntroduces() {
        for (lesson in curriculum.lessons.filter { it.newWords.isNotEmpty() }) {
            val options = curriculum.optionsFor(lesson, QuizOptions())
            val asked = engine.buildSkillIndex(options).values
                .flatMap { entries -> entries.map { engine.wordOf(it).key } }
                .toSet()
            val missing = lesson.newWords.filterNot { it in asked }
            assertEquals("${lesson.id} introduces words it never asks about", emptyList<String>(), missing)
        }
    }

    /**
     * The first lesson offers sixteen pairs and asks twelve questions. Drawing each one
     * independently repeated three or four of them, which is invisible on the big practice
     * pools and glaring in a lesson.
     */
    @Test
    fun aLessonNeverAsksTheSameQuestionTwice() {
        for (lesson in curriculum.lessons) {
            val pool = engine.buildPool(curriculum.optionsFor(lesson, QuizOptions()))
            val drawn = engine.buildQueue(pool, lesson.questions)
            assertEquals("${lesson.id} came up short", lesson.questions, drawn.size)
            assertEquals("${lesson.id} repeats a question", drawn.size, drawn.toSet().size)
        }
    }

    @Test
    fun aSessionLongerThanItsPoolFallsBackToRepeatsRatherThanStoppingShort() {
        val pool = engine.buildPool(curriculum.optionsFor(curriculum["start"]!!, QuizOptions()))
        val drawn = engine.buildQueue(pool, pool.size + 5)
        assertEquals(pool.size + 5, drawn.size)
        // Every distinct pair is still used before anything is repeated.
        assertEquals(pool.size, drawn.take(pool.size).toSet().size)
    }

    @Test
    fun aLessonOnlyAsksAboutItsOwnVocabulary() {
        val lesson = curriculum["past"]!!
        val allowed = curriculum.words(lesson.id)
        val options = curriculum.optionsFor(lesson, QuizOptions())
        val asked = engine.buildSkillIndex(options).values
            .flatMap { entries -> entries.map { engine.wordOf(it).key } }
            .toSet()
        assertTrue("asked outside its vocabulary", allowed.containsAll(asked))
    }

    @Test
    fun lessonsNeverAskTrickQuestions() {
        val options = curriculum.optionsFor(curriculum["past"]!!, QuizOptions())
        assertFalse(options.isOn(TransformationBuilder.TRICK))
    }

    @Test
    fun theFirstLessonOnlyUsesFormsItHasTaught() {
        val lesson = curriculum.lessons.first { it.requires.isEmpty() }
        val options = curriculum.optionsFor(lesson, QuizOptions())
        val forms = curriculum.forms(lesson.id)
        for (key in QuizOptions.FORM_KEYS) {
            assertEquals("form $key", key in forms, options.isOn(key))
        }
    }

    /**
     * Form option keys and transformation types are almost the same vocabulary, which is
     * exactly why the one mismatch (plain/polite both record as "politeness") went unseen:
     * the mastery ring silently matched nothing for the first lesson.
     */
    @Test
    fun everyFormOptionMapsOntoARealTransformationType() {
        val types = data.transformations.map { it.type }.toSet()
        for (key in QuizOptions.FORM_KEYS) {
            val mapped = TransformationBuilder.typeOfForm(key)
            assertTrue("form '$key' maps to '$mapped', which no transformation has", mapped in types)
        }
    }

    @Test
    fun aLessonsFormsMapOntoSkillsItCanActuallyRecord() {
        for (lesson in curriculum.lessons) {
            val types = curriculum.forms(lesson.id).map(TransformationBuilder::typeOfForm).toSet()
            val options = curriculum.optionsFor(lesson, QuizOptions())
            val recorded = engine.buildSkillIndex(options).keys.map(QuizEngine::typeOfSkill).toSet()
            assertTrue(
                "${lesson.id} records skills ${recorded - types} its forms cannot explain",
                types.containsAll(recorded),
            )
        }
    }

    // Grammar. The reference and the lesson intros share this content, so a form with no
    // note means both a gap in the list and a lesson that teaches a rule it never states.

    @Test
    fun everyFormOptionHasAGrammarNote() {
        for (key in QuizOptions.FORM_KEYS) {
            assertNotNull("form option '$key' has no grammar note", Grammar[key])
        }
        for (note in Grammar.NOTES) {
            assertTrue("grammar note '${note.key}' is not a form the drill offers", note.key in QuizOptions.FORM_KEYS)
        }
    }

    @Test
    fun everyClassALessonIsAboutHasANote() {
        val groups = data.words.map { it.group }.toSet()
        for (lesson in curriculum.lessons) {
            for (group in lesson.newClasses) {
                assertNotNull("${lesson.id} is about '$group', which has no class note", Grammar.classNote(group))
            }
        }
        for (note in Grammar.CLASS_NOTES) {
            assertTrue("class note '${note.key}' is not a word group", note.key in groups)
        }
    }

    /** Types reach the learner in the failure message; "politeness" is not a word they know. */
    @Test
    fun everyQuestionTypeHasALearnerFacingName() {
        for (type in data.transformations.map { it.type }.toSet()) {
            assertTrue("no label for question type '$type'", QuizOptions.FOCUS.any { it.key == type })
        }
    }

    /** A focus switches on the forms it asks about, so it can never empty the pool. */
    @Test
    fun choosingAFocusAlwaysLeavesSomethingToAsk() {
        for (focus in QuizOptions.FOCUS) {
            val pool = engine.buildPool(QuizOptions().withFocus(focus.key))
            assertFalse("focus '${focus.key}' leaves nothing to ask", pool.isEmpty)
        }
    }

    @Test
    fun everyPresetAsksSomething() {
        val passed = setOf(curriculum.lessons.first().id)
        val forms = passed.flatMapTo(HashSet()) { curriculum.forms(it) }
        val groups = passed.flatMap { curriculum.words(it) }.mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group }
        for (preset in PracticePreset.entries) {
            val options = QuizOptions().withPreset(preset, forms, groups)
            assertTrue("$preset selects neither plain nor polite", options.hasPoliteness)
            assertFalse("$preset leaves nothing to ask", engine.buildPool(options).isEmpty)
        }
    }

    @Test
    fun everyLessonIntroducesSomethingItCanExplain() {
        for (lesson in curriculum.lessons) {
            assertTrue(
                "${lesson.id} introduces nothing, so it would go straight to questions",
                lesson.newWords.isNotEmpty() || lesson.newForms.isNotEmpty(),
            )
            for (form in lesson.newForms) {
                assertNotNull("${lesson.id} adds '$form' with no grammar note", Grammar[form])
            }
        }
    }

    @Test
    fun everyFormCanBeShownBeingBuilt() {
        for (note in Grammar.NOTES) {
            val examples = Grammar.examplesFor(note.key).map { key ->
                assertTrue("'$key' is an example for ${note.key} but not in EXAMPLE_KEYS", key in Grammar.EXAMPLE_KEYS)
                data.wordsByKey[key] ?: error("grammar example '$key' is not in words.json")
            }
            val target = Grammar.conjugationOf(note.key) ?: continue
            val usable = examples.filter { it.conjugations[target]?.forms?.isNotEmpty() == true }
            assertTrue("no example word has a ${note.key} form", usable.isNotEmpty())
            for (word in usable) {
                assertTrue(
                    "${note.key} of ${word.key} derives no steps, so the card would be empty",
                    Explanations.solution(word, target).steps.isNotEmpty(),
                )
            }
        }
    }

    /**
     * The sound changes are shown as a table rather than derived per example word, so the
     * table has to be the whole story: every change a godan verb in the word list can
     * undergo must be a row in it.
     */
    @Test
    fun theFusionTableCoversEverySoundChange() {
        fun ending(word: Word): String? = word.conjugations["te-form"]?.forms?.firstOrNull()?.takeLast(2)

        val everyChange = data.words.filter { it.group == "godan" }.mapNotNull(::ending).toSet()
        assertEquals(everyChange, Explanations.GODAN_FUSIONS.map { it.te }.toSet())

        // And every ending a godan verb can have is named in exactly one row.
        val listed = Explanations.GODAN_FUSIONS.flatMap { it.endings }
        assertEquals(listed.size, listed.toSet().size)
        for (word in data.words.filter { it.group == "godan" }) {
            val last = word.dictionary.last()
            assertTrue("$last is not in the fusion table", last in listed)
        }
    }

    /** The reference reads ichidan first, then godan: the class with no table comes first. */
    @Test
    fun theSimplestVerbClassIsShownFirst() {
        for (note in Grammar.NOTES) {
            val groups = Grammar.examplesFor(note.key).mapNotNull { data.wordsByKey[it]?.group }
            assertEquals("${note.key} does not start with the ichidan example", "ichidan", groups.first())
        }
    }

    // Review

    @Test
    fun reviewSpansEverythingPassedAndNothingElse() {
        val passed = setOf("start", "negative")
        val words = passed.flatMapTo(HashSet()) { curriculum.words(it) }
        val forms = passed.flatMapTo(HashSet()) { curriculum.forms(it) }
        val options = curriculum.optionsFor(words, forms, QuizOptions())

        val index = engine.buildSkillIndex(options)
        assertTrue("review pool is empty", index.isNotEmpty())
        val asked = index.values.flatMap { entries -> entries.map { engine.wordOf(it).key } }.toSet()
        assertTrue(words.containsAll(asked))
    }

    // Scheduler

    @Test
    fun aCorrectAnswerPushesTheNextReviewOut() {
        var state = Scheduler.review(SrsState(), correct = true, today = 100)
        assertEquals("the first correct answer comes back tomorrow", 101L, state.due)

        // Each further correct answer must wait strictly longer than the one before it.
        var previous = state.due - 100
        repeat(Scheduler.LADDER.size - 1) {
            val today = state.due
            state = Scheduler.review(state, correct = true, today = today)
            val interval = state.due - today
            assertTrue("interval $interval did not grow past $previous", interval > previous)
            previous = interval
        }
    }

    @Test
    fun aLapseStepsBackOneRungRatherThanResetting() {
        var state = SrsState()
        repeat(4) { state = Scheduler.review(state, correct = true, today = state.due) }
        val before = state.step
        val lapsed = Scheduler.review(state, correct = false, today = state.due)

        assertEquals(before - 1, lapsed.step)
        assertEquals(1, lapsed.lapses)
        assertEquals(state.due + 1, lapsed.due)
        assertTrue("a lapse must make the item easier, not harder", lapsed.ease < state.ease)
    }

    @Test
    fun anItemNeverAnsweredCorrectlyIsNotTreatedAsLearned() {
        val wrong = Scheduler.review(SrsState(), correct = false, today = 10)
        assertEquals("a first miss must not climb onto the ladder", Scheduler.UNLEARNED, wrong.step)
        assertEquals(0f, Scheduler.strength(wrong), 0.001f)
        assertEquals(1, wrong.lapses)

        // ...and it must be distinguishable from having got it right first time.
        val right = Scheduler.review(SrsState(), correct = true, today = 10)
        assertTrue("right and wrong must not leave the same state", right.step != wrong.step)
        assertTrue(Scheduler.strength(right) > Scheduler.strength(wrong))
    }

    @Test
    fun easeStaysWithinBounds() {
        var hard = SrsState()
        repeat(30) { hard = Scheduler.review(hard, correct = false, today = hard.due) }
        assertTrue(hard.ease >= 0.6)
        assertEquals("never learned, so still below the ladder", Scheduler.UNLEARNED, hard.step)

        // An item that was learned and then repeatedly missed stops at the first rung
        // rather than dropping off the ladder entirely.
        var lapsed = Scheduler.review(SrsState(), correct = true, today = 0)
        repeat(30) { lapsed = Scheduler.review(lapsed, correct = false, today = lapsed.due) }
        assertEquals("a lapse never goes below the first rung", 0, lapsed.step)

        var easy = SrsState()
        repeat(30) { easy = Scheduler.review(easy, correct = true, today = easy.due) }
        assertTrue(easy.ease <= 1.4)
        assertEquals(Scheduler.LADDER.size - 1, easy.step)
    }

    @Test
    fun nothingIsScheduledBeyondAYear() {
        var state = SrsState()
        repeat(40) { state = Scheduler.review(state, correct = true, today = state.due) }
        val last = state.due
        val next = Scheduler.review(state, correct = true, today = last)
        assertTrue("interval must stay sane", next.due - last <= 365)
    }

    @Test
    fun anItemIsDueOnItsDueDay() {
        val state = Scheduler.review(SrsState(), correct = true, today = 10)
        assertFalse(Scheduler.isDue(state, 10))
        assertTrue(Scheduler.isDue(state, state.due))
        assertTrue(Scheduler.isDue(state, state.due + 5))
    }

    @Test
    fun strengthGrowsWithTheLadder() {
        var state = SrsState()
        var previous = Scheduler.strength(state)
        repeat(Scheduler.LADDER.size) {
            state = Scheduler.review(state, correct = true, today = state.due)
            assertTrue(Scheduler.strength(state) >= previous)
            previous = Scheduler.strength(state)
        }
        assertEquals(1f, Scheduler.strength(state), 0.001f)
    }

    // Progress

    @Test
    fun dueCountOnlyCountsWhatIsDue() {
        val progress = Progress(
            skills = mapOf(
                "past|godan" to SrsState(due = 5),
                "te-form|ichidan" to SrsState(due = 50),
            )
        )
        assertEquals(1, progress.dueCount(10))
        assertEquals(2, progress.dueCount(50))
        assertEquals(0, progress.dueCount(4))
    }

    // Backup. The stored document and the one the user copies out are the same text, so
    // these cover both saving and export/import.

    @Test
    fun aBackupRoundTripsEveryField() {
        val original = Progress(
            lessons = mapOf("start" to LessonRecord(passed = true, bestAccuracy = 0.92, attempts = 3)),
            skills = mapOf("past|godan" to SrsState(step = 2, ease = 1.1, due = 20715, reps = 5, lapses = 1)),
            words = mapOf("教える" to SrsState(step = 0, ease = 0.85, due = 20700, reps = 2, lapses = 2)),
            leeches = mapOf("教える|politeness" to 3),
        )
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original)))
        // Pretty-printing is only whitespace; it must decode to exactly the same thing.
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original, indent = 2)))
    }

    @Test
    fun anExportedBackupIsReadableText() {
        val text = ProgressCodec.encode(Progress(skills = mapOf("past|godan" to SrsState())), indent = 2)
        assertTrue("should be multi-line so it survives being pasted around", text.contains('\n'))
        assertTrue(text.contains("past|godan"))
    }

    @Test
    fun pastingSomethingElseIsRejectedRatherThanTreatedAsEmpty() {
        // The danger is a silent wipe: anything unrecognised has to fail, not decode to
        // an empty path that then replaces real progress.
        assertNull(ProgressCodec.decodeOrNull("hello"))
        assertNull(ProgressCodec.decodeOrNull(""))
        assertNull(ProgressCodec.decodeOrNull("{}"))
        assertNull(ProgressCodec.decodeOrNull("""{"lessons":{},"skills":{}}"""))
        assertNull(ProgressCodec.decodeOrNull("""{"version":999,"skills":{}}"""))
    }

    @Test
    fun surroundingWhitespaceFromACopyPasteIsTolerated() {
        val text = ProgressCodec.encode(Progress(leeches = mapOf("a|past" to 2)), indent = 2)
        assertEquals(mapOf("a|past" to 2), ProgressCodec.decodeOrNull("\n\n  $text  \n")?.leeches)
    }

    @Test
    fun skillKeysSplitBackIntoTheirFormName() {
        val word = data.words.first { it.group == "godan" }
        val t = data.transformations.first { it.type == "past" && !it.isTrick }
        assertEquals("past|godan", QuizEngine.skillOf(word, t))
        assertEquals("past", QuizEngine.typeOfSkill(QuizEngine.skillOf(word, t)))
    }
}
