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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Primer
import com.japanesedrills.quiz.Step
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.StepCard
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme

/** One chapter of the path, in path order. */
private data class Chapter(val title: String, val cards: List<StepCard>) {
    val ready: Int get() = cards.count { it.ready }
}

/** Consecutive steps sharing a chapter; the path is already in order. */
private fun chaptersOf(path: List<StepCard>): List<Chapter> {
    val chapters = ArrayList<Chapter>()
    for (card in path) {
        val last = chapters.lastOrNull()
        if (last != null && last.title == card.step.chapter) {
            chapters[chapters.lastIndex] = last.copy(cards = last.cards + card)
        } else {
            chapters += Chapter(card.step.chapter, listOf(card))
        }
    }
    return chapters
}

/**
 * The learn path: a recommended order through the drill, with nothing locked. Review comes
 * first once there is anything to review, because returning daily is the habit worth
 * building; the steps are the slower, weekly sense of progress.
 *
 * Steps are grouped into chapters, and every chapter folds down to its heading; only the one
 * holding the next step starts open, so the path reads as where you are rather than as
 * forty-odd rows.
 */
@Composable
fun LearnPathScreen(
    state: DrillUiState,
    onStep: (Step) -> Unit,
    onStepIntro: (Step) -> Unit,
    onReview: () -> Unit,
    onPrimer: () -> Unit,
    onToggleChapter: (title: String, open: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapters = remember(state.path) { chaptersOf(state.path) }

    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize().verticalScrollbar(list),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.started) {
            item { WelcomeCard(onPrimer) }
        } else {
            item {
                Text(
                    "${state.path.count { it.ready }} of ${state.path.size} steps ready",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.progress.skills.isNotEmpty()) {
            item { ReviewCard(state.dueCount, onReview) }
        }
        // Before anything is started the welcome card already says where to begin.
        val next = state.nextStep
        if (state.started && next != null) {
            item { NextUpCard(next, onStart = { onStep(next) }) }
        }
        chapters.forEachIndexed { index, chapter ->
            val open = state.chapterOpen[chapter.title] ?: chapter.cards.any { it.step == state.nextStep }
            item(key = "chapter-${chapter.title}") {
                ChapterHeader(
                    number = index + 1,
                    chapter = chapter,
                    open = open,
                    onToggle = { onToggleChapter(chapter.title, !open) },
                )
            }
            if (open) {
                items(chapter.cards, key = { it.step.id }) { card ->
                    StepRow(
                        card,
                        recommended = card.step == state.nextStep,
                        onClick = { onStep(card.step) },
                        onIntro = { onStepIntro(card.step) },
                    )
                }
            }
        }
    }
}

/**
 * A chapter's name and how far into it the learner has got. Tapping it folds or unfolds the
 * steps.
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
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${chapter.ready} of ${chapter.cards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (open) "Hide steps" else "Show steps",
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
                "Each step adds one form or a few new words. Take them in order or jump " +
                    "ahead — nothing is locked. Anything you practise comes back for review.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onPrimer,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Read first: ${Primer.TITLE}", style = MaterialTheme.typography.labelLarge)
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

/**
 * What the path recommends, with a way straight in. Readiness decides it, never a lock: the
 * learner can take any other step from the list below.
 */
@Composable
private fun NextUpCard(step: Step, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Next up", style = MaterialTheme.typography.labelLarge)
                FuriganaText(step.title, style = MaterialTheme.typography.titleMedium)
                FuriganaText(step.subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onStart) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Start")
            }
        }
    }
}

@Composable
private fun StepRow(card: StepCard, recommended: Boolean, onClick: () -> Unit, onIntro: () -> Unit) {
    val step = card.step
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        // The recommended row is marked by its fill alone, so nothing around it moves.
        colors = CardDefaults.cardColors(
            containerColor = if (recommended) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(card)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FuriganaText(step.title, style = MaterialTheme.typography.titleMedium)
                FuriganaText(
                    step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.newWords > 0) {
                    Text(
                        if (card.newWords == 1) "1 new word" else "${card.newWords} new words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // On every step that has an introduction, opened or not, so rows never change shape.
            if (card.hasIntro) {
                IconButton(onClick = onIntro) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "About this step",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A tick once the step has been ready, a play mark until then, and once started a ring
 * around it for how well the step's content is holding up in review, so it fades as a form
 * goes stale and gives the review queue a visible purpose.
 */
@Composable
private fun StatusBadge(card: StepCard) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (card.started) {
            CircularProgressIndicator(
                progress = { card.strength },
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = DrillTheme.answerColors.correct,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        if (card.ready) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Ready",
                tint = DrillTheme.answerColors.correct,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = if (card.started) "Started" else "Not started",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (card.started) 20.dp else 24.dp),
            )
        }
    }
}
