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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.LessonCard
import com.japanesedrills.ui.Recommendation
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.Lit
import com.japanesedrills.ui.components.Panel
import com.japanesedrills.ui.components.PanelPadding
import com.japanesedrills.ui.components.SectionPiece
import com.japanesedrills.ui.components.SectionSpacing
import com.japanesedrills.ui.components.StrengthBar
import com.japanesedrills.ui.components.TextAction
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import com.japanesedrills.ui.theme.lead

/** One chapter of the path, in path order. */
private data class Chapter(val title: String, val cards: List<LessonCard>) {
    val ready: Int get() = cards.count { it.ready }
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
 * The learn path: a recommended order through the drill, with nothing locked. Review comes
 * first once there is anything to review, because returning daily is the habit worth
 * building; the lessons are the slower, weekly sense of progress.
 *
 * A chapter is one section and its lessons are the rows in it, the shape the rest of the app
 * lists things in. Every chapter folds down to its heading row; only the one holding the next
 * lesson starts open, so the path reads as where you are rather than as forty-odd rows.
 */
@Composable
fun LearnPathScreen(
    state: DrillUiState,
    onLesson: (Lesson) -> Unit,
    /** Straight into a lesson's questions, past its introduction. */
    onStart: (Lesson) -> Unit,
    onReview: () -> Unit,
    onToggleChapter: (title: String, open: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Where the path is scrolled to, held by the caller so it outlasts a lesson played from it. */
    list: LazyListState = rememberLazyListState(),
) {
    val chapters = remember(state.path) { chaptersOf(state.path) }
    val recommendation = state.recommendation

    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize().verticalScrollbar(list),
        contentPadding = PaddingValues(16.dp),
    ) {
        // What the path asks for right now, in one panel. Whichever of the two it
        // actually wants is the lit half: colour says what to do first, and the order
        // never changes, so the screen reads the same way every day. Nothing is drawn
        // until review has been counted, which is what makes `recommendation` non-null,
        // so the lit half cannot start on the wrong row and swap under the reader.
        if (recommendation != null) {
            item(key = "today") {
                Spaced {
                    Panel {
                        val lead = state.reviewLeads
                        if (state.hasReview) {
                            if (lead) {
                                Lit { ReviewRow(state, onReview, lead = true) }
                            } else {
                                ReviewRow(state, onReview, lead = false)
                            }
                        }
                        if (lead) {
                            RecommendedRow(recommendation, onLesson, onReview, lead = false)
                        } else {
                            Lit { RecommendedRow(recommendation, onLesson, onReview, lead = true) }
                        }
                    }
                }
            }
        }
        item(key = "path") { PathHeading(ready = state.path.count { it.ready }, total = state.path.size) }
        // A chapter is one section, drawn a row at a time: composed whole, every row of it
        // would be built in the frame it scrolls into.
        chapters.forEachIndexed { index, chapter ->
            val open = state.chapterOpen[chapter.title] ?: chapter.cards.any { it.lesson == state.nextLesson }
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
                items(chapter.cards, key = { it.lesson.id }) { card ->
                    SectionPiece(first = false, last = card.lesson.id == chapter.cards.last().lesson.id) {
                        LessonRow(
                            card,
                            recommended = card.lesson == state.nextLesson,
                            onClick = { onLesson(card.lesson) },
                            onStart = { onStart(card.lesson) },
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
 * lessons.
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
            contentDescription = if (open) "Hide lessons" else "Show lessons",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun count(n: Int, noun: String): String =
    if (n == 1) "1 $noun" else "$n ${noun}s"

/**
 * What review would cost, not how much is waiting: the length of the session is what the
 * learner is deciding about, and it is no longer a fixed number. How many lessons are
 * waiting goes in the note underneath.
 */
@Composable
private fun ReviewRow(state: DrillUiState, onReview: () -> Unit, lead: Boolean) {
    val due = state.dueCount
    PanelRow(
        label = "Review",
        line = if (due == 0) "Nothing due" else count(state.dueQuestions, "question"),
        note = if (due == 0) null else "across ${count(due, "lesson")}",
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
    note: String? = null,
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
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lead) surfaces.onHeroVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
 * What the path wants next, whatever that is: the lesson when review is solid, review
 * itself when it is not, and free practice once every lesson is ready. One row, one label,
 * so the absence of a lesson is stated rather than left to be noticed.
 */
@Composable
private fun RecommendedRow(
    recommendation: Recommendation,
    onLesson: (Lesson) -> Unit,
    onReview: () -> Unit,
    lead: Boolean,
) {
    val title = when (recommendation) {
        is Recommendation.NextLesson -> recommendation.lesson.title
        Recommendation.ImproveReview -> "Improve Review"
        Recommendation.Done -> "Review or Practice"
    }
    PanelRow(
        label = "Recommended next",
        line = title,
        lead = lead,
        action = if (recommendation is Recommendation.NextLesson) "Open" else "Review",
        icon = if (recommendation is Recommendation.NextLesson) Icons.Default.PlayArrow else Icons.Default.Refresh,
        onClick = {
            if (recommendation is Recommendation.NextLesson) onLesson(recommendation.lesson) else onReview()
        },
    )
}

/**
 * One lesson, on one line: whether it has been ready, what it is called, how well its content
 * is holding up in review, and a way straight into its questions.
 *
 * Tapping the row reads the lesson; Start skips that. Start only comes once the lesson has
 * been played, so the first time through is always by way of what it teaches — and its
 * column is there either way, so the row does not change shape when it appears.
 *
 * One line and not three. The path is read down — dozens of lessons over several chapters —
 * and a subtitle under every one of them turned a list into a wall; the row only has to say
 * which lesson it is and where it stands.
 */
@Composable
private fun LessonRow(card: LessonCard, recommended: Boolean, onClick: () -> Unit, onStart: () -> Unit) {
    val lesson = card.lesson
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            // The recommended lesson is named on the panel above; here it is marked by the
            // well it sits in, which fills its own footprint rather than moving any row.
            .background(if (recommended) DrillTheme.surfaces.well else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp)
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
        FuriganaText(
            lesson.title,
            style = MaterialTheme.typography.heading,
            color = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The bar's width is the same on every row, empty or not, so the titles beside
        // them all end in one place and a reading lesson leaves a gap rather than a hole.
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(BarWidth)) {
            if (!lesson.reading) StrengthBar(card.strength, Modifier.fillMaxWidth())
        }
        Box(Modifier.width(StartWidth), contentAlignment = Alignment.CenterEnd) {
            if (card.started && !lesson.reading) TextAction("Start", onStart)
        }
    }
}

private val TickWidth = 26.dp
private val BarWidth = 62.dp
private val StartWidth = 58.dp

