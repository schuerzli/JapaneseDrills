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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Step
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.Recommendation
import com.japanesedrills.ui.StepCard
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.Lit
import com.japanesedrills.ui.components.Panel
import com.japanesedrills.ui.components.PanelPadding
import com.japanesedrills.ui.components.Section
import com.japanesedrills.ui.components.SectionPiece
import com.japanesedrills.ui.components.SectionSpacing
import com.japanesedrills.ui.components.StrengthBar
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import com.japanesedrills.ui.theme.lead
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
        // What the path asks for right now, in one panel. Whichever of the two it
        // actually wants is the lit half: colour says what to do first, and the order
        // never changes, so the screen reads the same way every day.
        if (state.hasReview || recommendation != null) {
            item {
                Spaced {
                    Panel {
                        val leadIsReview = state.hasReview
                        if (state.hasReview) {
                            if (leadIsReview) {
                                Lit { ReviewRow(state.dueCount, onReview, lead = true) }
                            } else {
                                ReviewRow(state.dueCount, onReview, lead = false)
                            }
                        }
                        if (recommendation != null) {
                            if (leadIsReview) {
                                RecommendedRow(recommendation, onStep, onReview, lead = false)
                            } else {
                                Lit { RecommendedRow(recommendation, onStep, onReview, lead = true) }
                            }
                        }
                    }
                }
            }
        }
        item { PathHeading(ready = state.path.count { it.ready }, total = state.path.size) }
        // A chapter is one card, drawn a row at a time: a card composed whole would be nine
        // rows built in the frame it scrolls into.
        chapters.forEachIndexed { index, chapter ->
            val open = state.chapterOpen[chapter.title] ?: chapter.cards.any { it.step == state.nextStep }
            item(key = "chapter-${chapter.title}") {
                SectionPiece(first = true, last = !open) {
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
                    SectionPiece(first = false, last = card.step.id == chapter.cards.last().step.id) {
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

/** A panel of its own, with the room between it and what follows under it. */
@Composable
private fun Spaced(content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = SectionSpacing)) { content() }
}

/**
 * Where today ends and the whole path begins. Centred between two rules, because it
 * divides the screen rather than titling a list: everything above it is this visit,
 * everything below it is the course.
 */
@Composable
private fun PathHeading(ready: Int, total: Int) {
    Column(
        Modifier.padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "The path",
                style = MaterialTheme.typography.heading,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            "$ready of $total lessons ready",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
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
private fun ReviewRow(due: Int, onReview: () -> Unit, lead: Boolean) {
    PanelRow(
        label = "Review",
        line = if (due == 0) "Nothing due" else "$due due today",
        lead = lead,
        action = "Review",
        icon = Icons.Default.Refresh,
        onClick = onReview,
    )
}

/**
 * One half of the today panel. The lit one carries the larger line and the filled button;
 * the other states where it stands and offers the same word without a shape round it.
 */
@Composable
private fun PanelRow(
    label: String,
    line: String,
    lead: Boolean,
    action: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val surfaces = DrillTheme.surfaces
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = PanelPadding,
                end = if (lead) PanelPadding else PanelPadding - 8.dp,
                top = if (lead) 16.dp else 10.dp,
                bottom = if (lead) 18.dp else 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (lead) surfaces.onHeroVariant else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FuriganaText(
                line,
                style = if (lead) MaterialTheme.typography.lead else MaterialTheme.typography.heading,
                color = if (lead) surfaces.onHero else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (lead) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = surfaces.onHero,
                    contentColor = surfaces.heroEnd,
                ),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(action)
            }
        } else {
            TextButton(onClick = onClick) { Text(action) }
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
    lead: Boolean,
) {
    val title = when (recommendation) {
        is Recommendation.Lesson -> recommendation.step.title
        Recommendation.ImproveReview -> "Improve Review"
        Recommendation.Done -> "Review or Practice"
    }
    PanelRow(
        label = "Recommended next",
        line = title,
        lead = lead,
        action = if (recommendation is Recommendation.Lesson) "Open" else "Review",
        icon = if (recommendation is Recommendation.Lesson) Icons.Default.PlayArrow else Icons.Default.Refresh,
        onClick = {
            if (recommendation is Recommendation.Lesson) onStep(recommendation.step) else onReview()
        },
    )
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
            if (!step.reading) StrengthBar(card.strength, Modifier.fillMaxWidth().padding(top = 2.dp))
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
