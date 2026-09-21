package com.japanesedrills.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.LessonCard
import com.japanesedrills.ui.theme.DrillTheme

/** One chapter of the path, in path order. */
private data class Chapter(val title: String, val cards: List<LessonCard>) {
    val passed: Int get() = cards.count { it.passed }

    /** Nothing in it can be started yet. */
    val locked: Boolean get() = cards.none { it.unlocked }

    /**
     * Open unless the learner says otherwise: only a chapter with a lesson to do next. A
     * finished chapter folds away like a locked one, or the top of the path would fill up
     * with ticked rows as the learner moves on.
     */
    val openByDefault: Boolean get() = cards.any { it.unlocked && !it.passed }
}

/** Consecutive lessons sharing a chapter; the path is already in order. */
private fun chaptersOf(path: List<LessonCard>): List<Chapter> {
    val chapters = ArrayList<Chapter>()
    for (card in path) {
        val last = chapters.lastOrNull()
        if (last != null && last.title == card.lesson.chapter) {
            chapters[chapters.lastIndex] = last.copy(cards = last.cards + card)
        } else {
            chapters += Chapter(card.lesson.chapter, listOf(card))
        }
    }
    return chapters
}

/**
 * The learn path: what to do next, and what it is worth. Review comes first once there is
 * anything to review, because returning daily is the habit worth building; the lesson list
 * is the slower, weekly sense of progress.
 *
 * Lessons are grouped into chapters, and every chapter folds down to its heading; only the
 * ones with a lesson to do next start open. Listed one by one, the locked tail was thirty identical padlocks: it said "a long
 * way to go" and nothing about what lay ahead.
 */
@Composable
fun LearnPathScreen(
    state: DrillUiState,
    onLesson: (Lesson) -> Unit,
    onReview: () -> Unit,
    onPrimer: () -> Unit,
    onToggleChapter: (title: String, open: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapters = remember(state.path) { chaptersOf(state.path) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.started) {
            item { WelcomeCard(onPrimer) }
        } else {
            item {
                Text(
                    "${state.progress.passed.size} of ${state.path.size} lessons passed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.progress.passed.isNotEmpty()) {
            item { ReviewCard(state.dueCount, onReview) }
        }
        chapters.forEachIndexed { index, chapter ->
            val open = state.chapterOpen[chapter.title] ?: chapter.openByDefault
            item(key = "chapter-${chapter.title}") {
                ChapterHeader(
                    number = index + 1,
                    chapter = chapter,
                    open = open,
                    onToggle = { onToggleChapter(chapter.title, !open) },
                )
            }
            if (open) {
                items(chapter.cards, key = { it.lesson.id }) { card ->
                    LessonRow(card, onClick = { onLesson(card.lesson) })
                }
            }
        }
    }
}

/**
 * A chapter's name and how far through it the learner is. Tapping it folds or unfolds the
 * lessons; a locked chapter says how many are inside rather than how many are passed.
 */
@Composable
private fun ChapterHeader(number: Int, chapter: Chapter, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Chapter $number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                chapter.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (chapter.locked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (chapter.locked) "${chapter.cards.size} lessons" else "${chapter.passed} of ${chapter.cards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (open) "Hide lessons" else "Show lessons",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The welcome, with the way into the primer. Every rule on the path is phrased in terms of
 * the kana grid and the verb classes, and the primer is the only place that explains them.
 */
@Composable
private fun WelcomeCard(onPrimer: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Start here", style = MaterialTheme.typography.titleLarge)
            Text(
                "Each lesson adds a little grammar or a few new words, and unlocks the next " +
                    "one when you pass. Anything you have learned comes back for review.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onPrimer,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Read first: how conjugation works", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(due: Int, onReview: () -> Unit) {
    val nothingDue = due == 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (nothingDue) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                DrillTheme.answerColors.correctContainer
            },
            contentColor = if (nothingDue) {
                MaterialTheme.colorScheme.onSurface
            } else {
                DrillTheme.answerColors.onCorrectContainer
            },
        ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Review", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (nothingDue) "Nothing due — everything is fresh" else "$due due today",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = onReview) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (nothingDue) "Practise" else "Review")
            }
        }
    }
}

@Composable
private fun LessonRow(card: LessonCard, onClick: () -> Unit) {
    val lesson = card.lesson
    val enabled = card.unlocked
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
        card.passed -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(card)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (card.passed) FontWeight.Normal else FontWeight.Medium,
                    color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    lesson.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lesson.newWords.isNotEmpty()) {
                    Text(
                        if (lesson.newWords.size == 1) "1 new word" else "${lesson.newWords.size} new words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Locked, ready, or passed with a mastery ring. The ring is the weakest skill in the
 * lesson, so it fades as a form goes stale and gives the review queue a visible purpose.
 */
@Composable
private fun StatusBadge(card: LessonCard) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when {
            !card.unlocked -> Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            card.passed -> {
                CircularProgressIndicator(
                    progress = { card.strength },
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = DrillTheme.answerColors.correct,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Passed",
                    tint = DrillTheme.answerColors.correct,
                    modifier = Modifier.size(20.dp),
                )
            }

            else -> Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Start",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
