package com.japanesedrills.quiz

import com.japanesedrills.data.Word

/**
 * A named collection of words free practice can draw on. Sets stack: switching several on
 * adds them together, and a word in three of them is still one word, because a set resolves
 * to word keys and the pool visits each word once.
 */
data class WordSet(val id: String, val label: String)

/**
 * A set the learner made: a name and the words in it, by key. It is their work and cannot be
 * rebuilt from the assets, so it is stored with their progress and travels in the backup.
 *
 * Word keys come from words.json, which is generated: regenerating it can retire a key. A
 * set therefore drops keys it no longer knows rather than failing to open.
 */
data class CustomSet(val id: String, val name: String, val words: Set<String> = emptySet())

/**
 * The sets the app ships with.
 *
 * The lists come from the JLPT and frequency tags in words.json, minus the four words that
 * follow their class except in a form or two — 行く, ある, いる and いい. They have no column
 * of their own on the practice grid, since each sits with its class, so a set of their own
 * is what makes them switchable at all. Leaving them out of the lists is what lets the sets
 * stack without a rule for subtracting: "JLPT N5" is then the regular N5 words, and adding
 * [PARTIAL] puts the awkward ones back.
 *
 * する verbs are not among them. A compound する verb is not an exception to a rule but one
 * pattern reused by hundreds of words, and the grid has a column for it.
 */
object WordSets {

    /** 行く, ある, いる and いい: regular in their class except in a form or two. */
    const val PARTIAL = "partial"

    /** The groups [PARTIAL] holds, which are also the groups the lists leave out. */
    val PARTIAL_GROUPS = setOf("iku", "aru", "iru", "ii")

    val BUILT_IN: List<WordSet> = listOf(
        WordSet("common", "Top 100 common verbs"),
        WordSet("n5", "JLPT N5"),
        WordSet("n4", "JLPT N4"),
        WordSet("n3", "JLPT N3"),
        WordSet("n2", "JLPT N2"),
        WordSet(PARTIAL, "Partially irregular"),
    )

    val IDS: Set<String> = BUILT_IN.mapTo(LinkedHashSet()) { it.id }

    /** Whether [word] belongs to the set [id], built in or one of [custom]. */
    fun holds(id: String, word: Word, custom: Map<String, CustomSet> = emptyMap()): Boolean = when {
        id == PARTIAL -> word.group in PARTIAL_GROUPS
        id in IDS -> id in word.tags && word.group !in PARTIAL_GROUPS
        else -> custom[id]?.words?.contains(word.key) == true
    }

    /** An id for a new set, unique among [taken]. */
    fun newId(taken: Set<String>): String = generateSequence(1) { it + 1 }
        .map { "set$it" }
        .first { it !in taken && it !in IDS }
}
