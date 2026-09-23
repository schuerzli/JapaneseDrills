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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.ConjugationIntro
import com.japanesedrills.quiz.Step
import com.japanesedrills.ui.DrillUiState
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
    onConjugationIntro: () -> Unit,
    onToggleChapter: (title: String, open: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapters = remember(state.path) { chaptersOf(state.path) }
    val next = state.nextStep

    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize().verticalScrollbar(list),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (!state.started) {
            item { Spaced { WelcomeCard(onConjugationIntro) } }
        } else {
            item {
                Text(
                    "${state.path.count { it.ready }} of ${state.path.size} steps ready",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SectionSpacing),
                )
            }
        }
        // What can be started right now, in one card: they are the same kind of thing, and
        // before anything is started the welcome card already says where to begin.
        if (state.progress.skills.isNotEmpty() || (state.started && next != null)) {
            item {
                Spaced {
                    SectionCard("Today") {
                        if (state.progress.skills.isNotEmpty()) ReviewRow(state.dueCount, onReview)
                        if (state.started && next != null) {
                            if (state.progress.skills.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            NextUpRow(next, onStart = { onStep(next) })
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

/**
 * The welcome, with the way into the Conjugation Intro. Every rule on the path is phrased in
 * terms of the kana grid and the verb classes, and the Conjugation Intro is the only place
 * that explains them.
 */
@Composable
private fun WelcomeCard(onConjugationIntro: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Start here", style = MaterialTheme.typography.heading)
            Text(
                "Each step adds one form or a few new words. Take them in order or jump " +
                    "ahead — nothing is locked. Anything you practise comes back for review.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onConjugationIntro,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Read first: ${ConjugationIntro.TITLE}", style = MaterialTheme.typography.labelLarge)
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
@Composable
private fun NextUpRow(step: Step, onStart: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FuriganaText("Next up · ${step.title}", style = MaterialTheme.typography.bodyLarge)
            FuriganaText(
                step.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onStart) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Start")
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
            StrengthBar(card.strength)
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
