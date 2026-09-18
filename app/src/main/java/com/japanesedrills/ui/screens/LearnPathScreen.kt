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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.ui.DrillUiState
import com.japanesedrills.ui.LessonCard
import com.japanesedrills.ui.theme.DrillTheme

/**
 * The learn path: what to do next, and what it is worth. Review comes first once there is
 * anything to review, because returning daily is the habit worth building; the lesson list
 * is the slower, weekly sense of progress.
 */
@Composable
fun LearnPathScreen(
    state: DrillUiState,
    onLesson: (Lesson) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.started) {
            item { WelcomeCard() }
        }
        if (state.progress.passed.isNotEmpty()) {
            item { ReviewCard(state.dueCount, onReview) }
        }
        items(state.path, key = { it.lesson.id }) { card ->
            LessonRow(card, onClick = { onLesson(card.lesson) })
        }
    }
}

@Composable
private fun WelcomeCard() {
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
            Button(onClick = onReview, enabled = true) {
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
                        "${lesson.newWords.size} new words",
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
