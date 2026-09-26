package com.japanesedrills.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Step
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.Recommendation
import com.japanesedrills.ui.StepCard
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.SectionCard
import com.japanesedrills.ui.components.SectionCardPiece
import com.japanesedrills.ui.components.SectionSpacing
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import kotlin.math.roundToInt

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
 * A chapter is one card and its steps are the rows in it, the shape the rest of the app
 * lists things in. Every chapter folds down to its heading row; only the one holding the next
 * step starts open, so the path reads as where you are rather than as forty-odd rows.
 */
@Composable
fun LearnPathScreen(
    state: DrillUiState,
    onStep: (Step) -> Unit,
    onStepIntro: (Step) -> Unit,
    onReview: () -> Unit,
    onToggleChapter: (title: String, open: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapters = remember(state.path) { chaptersOf(state.path) }
    val recommendation = state.recommendation

    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize().verticalScrollbar(list),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text(
                "${state.path.count { it.ready }} of ${state.path.size} lessons ready",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SectionSpacing),
            )
        }
        // What the path asks for right now, in one card: review first, because it is what
        // moves the lessons below, and the next lesson only when review has nothing waiting.
        if (state.hasReview || recommendation != null) {
            item {
                Spaced {
                    SectionCard("Today") {
                        if (state.hasReview) ReviewRow(state.dueCount, onReview)
                        if (recommendation != null) {
                            if (state.hasReview) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            RecommendedRow(recommendation, onStep = onStep, onReview = onReview)
                        }
                    }
                }
            }
        }
        // A chapter is one card, drawn a row at a time: a card composed whole would be nine
        // rows built in the frame it scrolls into.
        chapters.forEachIndexed { index, chapter ->
            val open = state.chapterOpen[chapter.title] ?: chapter.cards.any { it.step == state.nextStep }
            item(key = "chapter-${chapter.title}") {
                SectionCardPiece(first = true, last = !open) {
                    ChapterHeader(
                        number = index + 1,
                        chapter = chapter,
                        open = open,
                        onToggle = { onToggleChapter(chapter.title, !open) },
                    )
                }
            }
            if (open) {
                items(chapter.cards, key = { it.step.id }) { card ->
                    SectionCardPiece(first = false, last = card.step.id == chapter.cards.last().step.id) {
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
}

/** A card of its own, with the room between cards under it. */
@Composable
private fun Spaced(content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = SectionSpacing)) { content() }
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
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Chapter $number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(chapter.title, style = MaterialTheme.typography.heading)
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

@Composable
private fun ReviewRow(due: Int, onReview: () -> Unit) {
    val nothingDue = due == 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Review", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (nothingDue) "Nothing due — everything is fresh" else "$due due today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onReview) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (nothingDue) "Practise" else "Review")
        }
    }
}

/**
 * What the path recommends, with a way straight in. Readiness decides it, never a lock: the
 * learner can take any other step from the list below.
 */
/**
 * What the path wants next, whatever that is: the lesson when review is solid, review
 * itself when it is not, and free practice once every lesson is ready. One row, one label,
 * so the absence of a lesson is stated rather than left to be noticed.
 */
@Composable
private fun RecommendedRow(
    recommendation: Recommendation,
    onStep: (Step) -> Unit,
    onReview: () -> Unit,
) {
    val title = when (recommendation) {
        is Recommendation.Lesson -> recommendation.step.title
        Recommendation.ImproveReview -> "Improve Review"
        Recommendation.Done -> "Review or Practice"
    }
    val subtitle = when (recommendation) {
        is Recommendation.Lesson -> recommendation.step.subtitle
        Recommendation.ImproveReview -> "A new lesson once review is holding up"
        Recommendation.Done -> "Every lesson is ready; review keeps them that way"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FuriganaText("Recommended next · $title", style = MaterialTheme.typography.bodyLarge)
            FuriganaText(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        when (recommendation) {
            is Recommendation.Lesson -> Button(onClick = { onStep(recommendation.step) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Open")
            }
            // Both other cases point at review, which is the button above; this one is
            // outlined so the card never shows two filled buttons.
            else -> OutlinedButton(onClick = onReview) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Review")
            }
        }
    }
}

/**
 * One step: a tick once it has been ready, what it is about, and a bar for how well its
 * content is holding up in review.
 */
@Composable
private fun StepRow(card: StepCard, recommended: Boolean, onClick: () -> Unit, onIntro: () -> Unit) {
    val step = card.step
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                stateDescription = when {
                    card.ready -> "Ready"
                    card.started -> "Started"
                    else -> "Not started"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The column is there whether or not a tick is, so every title starts in one place.
        Box(Modifier.width(TickWidth)) {
            if (card.ready) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FuriganaText(
                step.title,
                style = MaterialTheme.typography.heading,
                // The recommended step is named on the card above; here it is only marked,
                // by colour rather than by a fill, so no row changes size.
                color = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            FuriganaText(
                if (card.newWords > 0) "${step.subtitle} · ${words(card.newWords)}" else step.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!step.reading) StrengthBar(card.strength)
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

private fun words(count: Int): String = if (count == 1) "1 new word" else "$count new words"

private val TickWidth = 26.dp

/**
 * How well a step's content is holding up in review, as a bar that takes its colour from how
 * far it has filled ([com.japanesedrills.ui.theme.StrengthColors]): a step that has never
 * been answered shows the empty track, and one that is solid reads green across.
 */
@Composable
private fun StrengthBar(strength: Float) {
    val filled = strength.coerceIn(0f, 1f)
    Box(
        Modifier
            .padding(top = 3.dp)
            .fillMaxWidth()
            .height(BarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { stateDescription = "${(filled * 100).roundToInt()} percent in review" },
    ) {
        if (filled > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(filled)
                    .height(BarHeight)
                    .clip(CircleShape)
                    .background(DrillTheme.strengthColors.at(filled)),
            )
        }
    }
}

private val BarHeight = 4.dp
