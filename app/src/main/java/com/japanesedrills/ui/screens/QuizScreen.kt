package com.japanesedrills.ui.screens

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanesedrills.data.DrillData
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.RomajiConverter
import com.japanesedrills.ui.QuizState
import com.japanesedrills.ui.components.AlignedChanges
import com.japanesedrills.ui.components.ChangeRow
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.JapaneseLocale
import com.japanesedrills.ui.components.LocalFurigana
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.StepBlock
import com.japanesedrills.ui.components.Subheading
import com.japanesedrills.ui.components.verticalScrollWithScrollbar
import com.japanesedrills.ui.theme.DrillTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quiz: QuizState,
    options: QuizOptions,
    onSubmit: (String) -> Unit,
    onProceed: () -> Unit,
    onExplain: () -> Unit,
    onToggleFurigana: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val question = quiz.question
    val answer = quiz.answer
    val scroll = rememberScrollState()
    val number = minOf(quiz.history.size + if (answer == null) 1 else 0, quiz.total)
    val progress by animateFloatAsState(quiz.history.size / quiz.total.toFloat(), label = "progress")

    // A new question, or a fresh verdict, belongs at the top. The explanation is the one
    // thing that opens further down, and it scrolls itself into view instead.
    LaunchedEffect(question.id, answer) {
        if (!quiz.showExplanation) scroll.animateScrollTo(0)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Question $number of ${quiz.total}") },
                    navigationIcon = {
                        IconButton(onClick = onQuit) {
                            Icon(Icons.Default.Close, contentDescription = "Quit")
                        }
                    },
                    actions = {
                        ScoreBadge(quiz.history.count { it.correct })
                        Spacer(Modifier.width(12.dp))
                    },
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScrollWithScrollbar(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuestionCard(quiz, options, onToggleFurigana)

            if (answer == null) {
                AnswerInput(
                    questionId = question.id,
                    shakes = quiz.shakes,
                    kana = options.kana,
                    // Useful the first time or two; after that it is furniture above the fold.
                    showHint = quiz.history.size < ROMAJI_HINT_QUESTIONS,
                    onSubmit = onSubmit,
                )
            } else {
                ResultCard(
                    quiz = quiz,
                    options = options,
                    focusNext = !quiz.showExplanation,
                    onExplain = onExplain,
                    onProceed = onProceed,
                )
                if (quiz.showExplanation) {
                    Explanation(quiz, options, onProceed)
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(correct: Int) {
    val colors = DrillTheme.answerColors
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.correctContainer,
        contentColor = colors.onCorrectContainer,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = "Correct answers", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("$correct", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QuestionCard(quiz: QuizState, options: QuizOptions, onToggleFurigana: () -> Unit) {
    val question = quiz.question
    val formLabel = Prompts.formLabel(question.transformation.phrase)
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val given = question.givenDisplay(options.kana)
    // Nothing to read in kana mode, or in a word with no kanji, so no switch either.
    val switchable = Furigana.hasReading(given)

    Card(
        onClick = onToggleFurigana,
        enabled = switchable,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        // The disabled colours would grey the card out; being untappable is not a state to show.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = onContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = onContainer,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Prompts.INSTRUCTION,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    formLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            RichText(
                parts = listOf(RichPart.Jp(given)),
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium),
                color = onContainer,
                horizontalArrangement = Arrangement.Center,
                // Tapping the card switches readings; the word must not jump when it does.
                reserveReadingSpace = true,
            )
            // The line is kept, empty, for a word with nothing to read, so the card does not
            // change height from one question to the next.
            if (!options.kana) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !switchable -> ""
                        LocalFurigana.current -> "Tap to hide readings"
                        else -> "Tap to show readings"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnswerInput(questionId: Int, shakes: Int, kana: Boolean, showHint: Boolean, onSubmit: (String) -> Unit) {
    var value by remember(questionId) { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }

    LaunchedEffect(questionId) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(shakes) {
        if (shakes == 0) return@LaunchedEffect
        for (target in listOf(-12f, 12f, -9f, 9f, -5f, 5f, 0f)) {
            shake.animateTo(target, tween(durationMillis = 45))
        }
    }

    val submit = { onSubmit(value.text) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                // Convert only what precedes the caret, so editing mid-word keeps the caret
                // in place instead of jumping to the end.
                val caret = new.selection.end
                val head = RomajiConverter.convert(new.text.take(caret))
                val converted = head + new.text.drop(caret)
                value = if (converted == new.text) new else TextFieldValue(converted, TextRange(head.length))
            },
            // No kanji in hiragana mode, the label included.
            label = { FuriganaText(if (kana) "Answer (こたえ)" else "Answer (答[こた]え)", style = LocalTextStyle.current) },
            supportingText = if (showHint) {
                { Text("Type romaji (e.g. \"tabenai\") or use a Japanese keyboard. Use \"nn\" for ん.") }
            } else {
                null
            },
            textStyle = answerStyle().copy(textAlign = TextAlign.Center),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                .focusRequester(focusRequester),
        )
        Button(onClick = submit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Check")
        }
    }
}

private fun answerStyle() = TextStyle(fontSize = 26.sp, localeList = JapaneseLocale)

/** How many questions of a session show the romaji hint under the answer field. */
private const val ROMAJI_HINT_QUESTIONS = 2

@Composable
private fun ResultCard(
    quiz: QuizState,
    options: QuizOptions,
    focusNext: Boolean,
    onExplain: () -> Unit,
    onProceed: () -> Unit,
) {
    val answer = quiz.answer ?: return
    val correct = answer.correct
    val answerColors = DrillTheme.answerColors
    val scheme = MaterialTheme.colorScheme
    val container = if (correct) answerColors.correctContainer else scheme.errorContainer
    val onContainer = if (correct) answerColors.onCorrectContainer else scheme.onErrorContainer

    // Like the web drill, move focus to "Next" so a hardware Enter continues instead of hitting "close".
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(focusNext) { if (focusNext) nextFocus.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // What the user typed.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (correct) answerColors.correct else scheme.error,
                    contentColor = if (correct) answerColors.onCorrect else scheme.onError,
                ) {
                    Icon(
                        if (correct) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (correct) "Correct" else "Incorrect",
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                RichText(listOf(RichPart.Jp(answer.responseDisplay)), style = MaterialTheme.typography.titleLarge)
            }
        }

        // The correct answer and what to do next.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!correct) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("The correct answer was", style = MaterialTheme.typography.bodyLarge)
                        for (line in Prompts.alternatives(quiz.question.answersDisplay(options.kana))) {
                            RichText(parts = line, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (!correct && !quiz.showExplanation) {
                        OutlinedButton(onClick = onExplain) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Explain")
                        }
                    }
                    Button(onClick = onProceed, modifier = Modifier.focusRequester(nextFocus)) {
                        Text(if (quiz.history.size >= quiz.total) "Results" else "Next")
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Explanation(quiz: QuizState, options: QuizOptions, onProceed: () -> Unit) {
    val question = quiz.question
    val t = question.transformation
    val word = question.word
    val body = MaterialTheme.typography.bodyLarge
    val uriHandler = LocalUriHandler.current
    val groupLabel = QuizEngine.groupLabels[word.group] ?: word.group

    // Tapping "Explain" adds this card below everything already on screen, so without
    // asking for it the card opens out of sight and looks as though nothing happened.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { bringIntoView.bringIntoView() }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Subheading("Goal")
            // The given word is already shown in the question card, so only the forms are
            // compared here, as a change like any other, with what the question changes marked.
            val goalFrom = formParts(t.fromTags, t.toTags)
            AlignedChanges(listOf(goalFrom)) { ChangeRow(goalFrom, formParts(t.toTags, t.fromTags)) }
            if (t.isTrick) {
                Text(
                    "It is already in that form — this was a trick question.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Subheading("Root word")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RichText(
                    listOf(RichPart.Jp(question.dictionaryDisplay(options.kana))),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://jisho.org/search/" + Uri.encode(Furigana.toKanji(word.dictionary)))
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Look up", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                // The words' notes only restate these two facts in a longer form, so they are not shown.
                listOfNotNull(
                    groupLabel,
                    word.tags.firstOrNull { it == "transitive" || it == "intransitive" },
                    word.meaning,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Supporting material: a quiet example of the word in use.
            if (word.sentenceJp.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    FuriganaText(
                        if (options.kana) Furigana.toKana(word.sentenceJp) else word.sentenceJp,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        word.sentenceEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Subheading("Solution")
            // Asked for the dictionary form, there is nothing to build: the answer is where the
            // building starts. So show how the given form was built from it, to be undone.
            // Every focused step asks both ways, which made this half of all explanations.
            val reverse = t.to == DrillData.DICTIONARY && t.from != DrillData.DICTIONARY
            val solution = remember(question.id) { Explanations.solution(word, if (reverse) t.from else t.to) }
            val display: (String) -> String = { if (options.kana) Furigana.toKana(it) else it }
            if (reverse) {
                RichText(
                    listOf(
                        RichPart.Text("The answer is the dictionary form. This is how "),
                        RichPart.Jp(display(word.dictionary)),
                        RichPart.Text(" becomes the form you were given; undo the steps to get back to it."),
                    ),
                    style = body,
                )
            } else if (solution.steps.isEmpty()) {
                Text("This is the dictionary form itself, so nothing needs to be added.", style = body)
            }
            val changes = solution.steps.map { step -> Prompts.changes(step.from.map(display), step.to.map(display), step.shape) }
            AlignedChanges(changes.flatten().map { it.first }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    solution.steps.forEachIndexed { i, step ->
                        StepBlock(
                            number = (i + 1).takeIf { solution.steps.size > 1 },
                            title = step.label,
                            rule = step.rule.map {
                                when (it) {
                                    is RichPart.Jp -> RichPart.Jp(display(it.word))
                                    is RichPart.Text -> it.copy(text = display(it.text))
                                    is RichPart.Tag, is RichPart.Marked -> it
                                }
                            },
                            changes = changes[i],
                        )
                    }
                }
            }

            val proceedFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { proceedFocus.requestFocus() }
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .align(Alignment.End)
                    .focusRequester(proceedFocus),
            ) {
                Text("OK, next question")
            }
        }
    }
}

/**
 * A form's tags as the goal shows them, in words, with those not in [other] marked: the part
 * of the form the question asks to change.
 */
private fun formParts(tags: List<String>, other: List<String>): List<RichPart> =
    tags.flatMapIndexed { i, tag ->
        listOfNotNull(
            RichPart.Text(" ").takeIf { i > 0 },
            RichPart.Text(if (tag == "te-form") "て-form" else tag, emphasis = tag !in other),
        )
    }
