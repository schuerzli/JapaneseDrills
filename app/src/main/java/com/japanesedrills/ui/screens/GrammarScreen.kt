package com.japanesedrills.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.ChangeShape
import com.japanesedrills.quiz.ConjugationIntro
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.FusionColumn
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.GrammarNote
import com.japanesedrills.quiz.Prompts
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.SolutionStep
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCard
import com.japanesedrills.ui.components.verticalScrollbar
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.heading
import com.japanesedrills.ui.theme.subheading

/**
 * Every form the drill can ask about, then every word type a step introduces. Tapping one
 * opens its note: for a form, what it means and how it is built.
 */
@Composable
fun GrammarScreen(
    onConjugationIntro: () -> Unit,
    onForm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize().verticalScrollbar(list),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Every form the drill asks about, what it is for, and how it is built.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        // First, and marked out from the list: the rest of this screen assumes you know
        // what a godan verb is, and this is where that is explained.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConjugationIntro),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ConjugationIntro.TITLE, style = MaterialTheme.typography.heading)
                        Text(ConjugationIntro.SUMMARY, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        item(key = "forms") { ListHeading("Forms") }
        items(Grammar.NOTES, key = { it.key }) { note -> NoteRow(note) { onForm(note.key) } }
        // A step shows these once, when it is first opened; this is where they are found again.
        item(key = "classes") { ListHeading("Word classes") }
        items(Grammar.CLASS_NOTES, key = { "class-${it.key}" }) { note -> NoteRow(note) { onForm(note.key) } }
    }
}

@Composable
private fun ListHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun NoteRow(note: GrammarNote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FuriganaText(note.title, style = MaterialTheme.typography.heading)
                FuriganaText(
                    note.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One form in full: what it is for, then how each word class builds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarDetailScreen(
    note: GrammarNote,
    examples: GrammarExamples,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { FuriganaText(note.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back to the form list")
                    }
                },
            )
        },
    ) { padding ->
        val list = rememberLazyListState()
        LazyColumn(
            state = list,
            modifier = Modifier.padding(padding).verticalScrollbar(list),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GrammarUsage(note) }
            // A word class has no construction of its own: the form notes show it on examples.
            if (note in Grammar.NOTES) item { GrammarConstruction(note, examples) }
        }
    }
}

/**
 * The prose half: what the form means and when it is reached for. [heading] defaults to
 * the question the card answers; where several notes share a screen, their titles say
 * which is which.
 */
@Composable
fun GrammarUsage(note: GrammarNote, heading: String = "What it is for") {
    SectionCard(heading) {
        FuriganaText(note.summary, style = MaterialTheme.typography.bodyLarge)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (line in note.notes) {
                // On the text's baseline, or a reading over the first line lifts the bullet above it.
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
                    FuriganaText(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
                }
            }
        }
    }
}

/**
 * The construction half, derived per word class from the same engine that explains a wrong
 * answer, so it cannot disagree with what the drill accepts. A class with no such form —
 * an adjective has no passive — simply does not appear.
 */
@Composable
fun GrammarConstruction(note: GrammarNote, examples: GrammarExamples) {
    val words = Grammar.examplesFor(note.key).mapNotNull(examples::get)
    val target = Grammar.conjugationOf(note.key)
    if (target == null) {
        SectionCard("How it is built", "Nothing to build — this is the form words are listed in") {
            for (word in words) {
                RichText(
                    listOf(RichPart.Jp(word.dictionary), RichPart.Text("  ${word.meaning}")),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        return
    }

    val derived = words
        .filter { word -> word.conjugations[target]?.forms?.isNotEmpty() == true }
        .map { word -> word to Explanations.solution(word, target).steps }

    // An irregular verb that inherits this form from its class is dropped: its entry would
    // repeat the rule above it and only change the word being named. 行く is irregular in
    // the forms built on its て-form and ordinary everywhere else, and the section is for
    // verbs that are irregular *here*.
    val shown = derived.filter { (word, _) ->
        word.group !in Grammar.IRREGULAR_GROUPS + Grammar.EXCEPTION_GROUPS || examples.declaresOwnRule(word, target)
    }

    SectionCard("How it is built", "Starting from the dictionary form") {
        // Grouped by heading rather than one heading per word: する, 来る and 行く are worth
        // meeting as "the irregulars" rather than as three unrelated classes.
        grouped(shown).forEachIndexed { index, (heading, group) ->
            // The class dividers get room of their own, so they read as the card's sections
            // and the hairlines of a fusion table inside one do not.
            if (index > 0) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    heading,
                    style = MaterialTheme.typography.subheading,
                    color = if (DrillTheme.accents.headings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val froms = group.flatMap { (_, steps) ->
                    steps.flatMap { step -> Prompts.changes(step.from, step.to, step.shape).map { it.first } }
                }
                AlignedChanges(froms) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Examples after the first state only what each step's rule adds to the
                        // first one's, so 買う shows "う becomes わ" instead of the whole sentence
                        // again, and a step every word takes the same way shows just its change.
                        val lead = group.first().second
                        group.forEachIndexed { i, (_, steps) ->
                            steps.forEachIndexed { j, step ->
                                // A fusion is a closed list, not a rule, so it gets the table
                                // itself, once, with the first step that uses it. A step that is
                                // nothing but the fusion would only repeat a row of the table.
                                val table = step.fusion?.takeIf { i == 0 }
                                val rule = when {
                                    step.fusion != null && step.shape == ChangeShape.Fuse() ->
                                        if (table != null) listOf(RichPart.Text("Fusion system")) else emptyList()
                                    i > 0 && steps.size == lead.size -> extensionOf(lead[j].rule, step.rule) ?: step.rule
                                    else -> step.rule
                                }
                                StepBlock(number = (j + 1).takeIf { steps.size > 1 }, rule = rule, table = table, step = step)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One step: its number when the form takes more than one, its rule, and its change. The
 * number column is there even when empty, so every step sits inset under its heading.
 */
@Composable
private fun StepBlock(number: Int?, rule: List<RichPart>, table: FusionColumn?, step: SolutionStep) {
    Row {
        Text(
            number?.toString().orEmpty(),
            // The outline colour keeps the numbers quieter than the text they number; the
            // contrast check holds it to the text floor on the card.
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(StepInset).alignByBaseline(),
        )
        Column(Modifier.alignByBaseline(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (rule.isNotEmpty()) {
                RichText(
                    // A label such as "Irregular" is the whole rule here, so it reads as one.
                    rule.map { if (it is RichPart.Tag) RichPart.Text(it.text) else it },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (table != null) FusionTable(table)
            for ((from, to) in Prompts.changes(step.from, step.to, step.shape)) ChangeRow(from, to)
        }
    }
}

private val StepInset = 30.dp

/** How wide the "from" column of [ChangeRow]s is, so their arrows line up. */
private val LocalFromWidth = compositionLocalOf { Dp.Unspecified }

/**
 * Lines up the arrows of every [ChangeRow] in [content]: the "from" column is as wide as the
 * widest of [froms], but never more than half the width, so a long word wraps rather than
 * pushing its result off the card.
 */
@Composable
private fun AlignedChanges(froms: List<List<RichPart>>, content: @Composable () -> Unit) {
    val style = MaterialTheme.typography.bodyLarge
    SubcomposeLayout { constraints ->
        val widest = subcompose("froms") { froms.forEach { RichText(it, style = style) } }
            .maxOfOrNull { it.measure(Constraints()).width } ?: 0
        val width = minOf(widest, constraints.maxWidth / 2).toDp()
        val body = subcompose("body") {
            CompositionLocalProvider(LocalFromWidth provides width, content = content)
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, body.sumOf { it.height }) {
            var y = 0
            for (placeable in body) {
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

/**
 * One change, "from → to", in columns. The arrow shares the words' baseline: furigana makes
 * the Japanese taller at the top, so centring would float it above them.
 */
@Composable
private fun ChangeRow(from: List<RichPart>, to: List<RichPart>) {
    val style = MaterialTheme.typography.bodyLarge
    Row {
        val width = LocalFromWidth.current
        RichText(from, style = style, modifier = (if (width.isSpecified) Modifier.width(width) else Modifier).alignByBaseline())
        Text(
            "→",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp).alignByBaseline(),
        )
        RichText(to, style = style, modifier = Modifier.weight(1f).alignByBaseline())
    }
}

/**
 * The godan fusions, with only the column [column] uses: the dictionary endings right-aligned
 * against what they fuse into, between hairlines rather than on a panel, so the table stays
 * lighter than the examples it serves.
 */
@Composable
private fun FusionTable(column: FusionColumn) {
    val style = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(vertical = 2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(vertical = 4.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                for (fusion in Explanations.GODAN_FUSIONS) {
                    RichText(listOf(RichPart.Jp(fusion.endings.joinToString(" · "))), style = style)
                }
            }
            Column(Modifier.padding(start = 14.dp)) {
                for (fusion in Explanations.GODAN_FUSIONS) {
                    val ending = if (column == FusionColumn.TE_FORM) fusion.te else fusion.past
                    RichText(listOf(RichPart.Jp(ending)), style = style)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The part of [rule] that goes beyond [shown], or null when [rule] does not begin with it.
 *
 * Rules are whole sentences, so [shown] has to end one; that keeps a shared first word from
 * being mistaken for a shared rule.
 */
private fun extensionOf(shown: List<RichPart>, rule: List<RichPart>): List<RichPart>? {
    if (shown.isEmpty() || rule.size < shown.size) return null
    val last = shown.lastIndex
    for (i in 0 until last) if (rule[i] != shown[i]) return null
    val head = shown[last] as? RichPart.Text ?: return null
    val same = rule[last] as? RichPart.Text ?: return null
    if (!head.text.endsWith(".") || !same.text.startsWith(head.text)) return null
    val rest = same.text.removePrefix(head.text).trimStart()
    return listOfNotNull(same.copy(text = rest).takeIf { rest.isNotEmpty() }) + rule.drop(shown.size)
}

/** 行く and ある: godan verbs, each irregular in a few forms. */
private val GODAN_EXCEPTIONS = setOf("iku", "aru")

/**
 * [shown] under its headings, in example order except that the godan verbs with an exception,
 * 行く and ある, follow the godan verbs: they are godan verbs, not irregular ones.
 */
private fun grouped(shown: List<Pair<Word, List<SolutionStep>>>): List<Pair<String, List<Pair<Word, List<SolutionStep>>>>> {
    val groups = shown.groupBy { (word, _) -> headingFor(word) }.toList()
    fun classOf(group: Pair<String, List<Pair<Word, List<SolutionStep>>>>) = group.second.first().first.group
    val exceptions = groups.filter { classOf(it) in GODAN_EXCEPTIONS }
    if (groups.none { classOf(it) == "godan" }) return groups
    return (groups - exceptions.toSet()).flatMap { if (classOf(it) == "godan") listOf(it) + exceptions else listOf(it) }
}

private fun headingFor(word: Word): String =
    if (word.group in Grammar.IRREGULAR_GROUPS) "Irregular verbs"
    else QuizEngine.groupLabels[word.group] ?: word.group
