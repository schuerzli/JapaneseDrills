package com.japanesedrills.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.japanesedrills.R
import com.japanesedrills.ui.components.HeroBar
import com.japanesedrills.ui.components.Section
import com.japanesedrills.ui.components.TextAction
import com.japanesedrills.ui.components.heroBarColors
import com.japanesedrills.ui.components.verticalScrollWithScrollbar

/**
 * One open dataset the word list was built from. The EDRDG licence asks that apps using
 * JMdict acknowledge it somewhere reachable such as an "About" screen, and CC BY-SA
 * requires naming the author, the licence and the fact that the data was modified.
 */
private data class Source(
    val name: String,
    val usedFor: String,
    val credit: String,
    val url: String,
    val licence: String? = null,
)

/** The web drills this app was modelled on. */
private val ORIGINALS = listOf(
    Source(
        name = "Don's Japanese Conjugation Drill",
        usedFor = "The original drill: its question format, conjugation rules and romaji input.",
        credit = "wkdonc",
        url = "https://wkdonc.github.io/conjugation/drill.html",
    ),
    Source(
        name = "LanDon's Japanese Verb Conjugation Drill",
        usedFor = "The extended fork this app was ported from.",
        credit = "LandonJPGinn",
        url = "https://landonjpginn.github.io/jp-verb-quiz/conjugation/drill.html",
    ),
)

private val SOURCES = listOf(
    Source(
        name = "JMdict / EDICT",
        usedFor = "Dictionary entries, word classes and English meanings.",
        credit = "© James William Breen and The Electronic Dictionary Research and Development Group",
        licence = "CC BY-SA 4.0",
        url = "https://www.edrdg.org/edrdg/licence.html",
    ),
    Source(
        name = "Tanaka Corpus",
        usedFor = "The example sentence shown with each word.",
        credit = "Maintained by the Tatoeba Project",
        licence = "CC BY",
        url = "https://tatoeba.org/",
    ),
    Source(
        name = "JmdictFurigana",
        usedFor = "Which kanji each reading belongs to, so furigana sit over the right character.",
        credit = "Doublevil",
        licence = "CC BY-SA 4.0",
        url = "https://github.com/Doublevil/JmdictFurigana",
    ),
    Source(
        name = "jmdict-simplified",
        usedFor = "The JSON form of JMdict this word list was extracted from.",
        credit = "scriptin",
        licence = "CC BY-SA 4.0",
        url = "https://github.com/scriptin/jmdict-simplified",
    ),
    Source(
        name = "open-anki-jlpt-decks",
        usedFor = "The JLPT level of each word.",
        credit = "© 2020 Jamie Sinclair",
        licence = "MIT",
        url = "https://github.com/jamsinclair/open-anki-jlpt-decks",
    ),
)

/**
 * Bundled fonts. The OFL asks that the licence travel with them where a user can read it: it
 * is res/raw/ofl.txt, shown in full under them ([FontLicence]).
 */
private val TYPEFACES = listOf(
    Source(
        name = "Lora",
        usedFor = "Titles in the Latte theme.",
        credit = "The Lora Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Lora",
    ),
    Source(
        name = "Manrope",
        usedFor = "Text in the Latte and Mocha themes.",
        credit = "The Manrope Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Manrope",
    ),
    Source(
        name = "Outfit",
        usedFor = "The Washi theme.",
        credit = "The Outfit Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Outfit",
    ),
    Source(
        name = "Fraunces",
        usedFor = "Titles in the Caramel theme.",
        credit = "The Fraunces Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Fraunces",
    ),
    Source(
        name = "Nunito",
        usedFor = "The Kissaten theme.",
        credit = "The Nunito Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Nunito",
    ),
    Source(
        name = "Nunito Sans",
        usedFor = "Text in the Caramel theme.",
        credit = "The Nunito Sans Project Authors",
        licence = "SIL Open Font License 1.1",
        url = "https://fonts.google.com/specimen/Nunito+Sans",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HeroBar {
                LargeTopAppBar(
                    colors = heroBarColors(),
                    title = { Text("About") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScrollWithScrollbar()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Based on", "This app is an Android version of two web drills.") {
                SourceList(ORIGINALS)
            }

            Section(
                "Word data",
                "The words, their readings, meanings and JLPT levels come from these open datasets.",
            ) {
                SourceList(SOURCES)
            }

            Section("Typefaces", "Japanese is set in the system's own font; these cover the rest.") {
                SourceList(TYPEFACES)
                FontLicence()
            }

            Section("Licence") {
                Row {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "JMdict and the files derived from it are licensed CC BY-SA 4.0. The word " +
                            "list in this app is a modified extract of them — entries were selected, " +
                            "regrouped and rewritten into this app's own format — and it is shared " +
                            "under that same licence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Conjugation rules and grammar explanations are this app's own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Like entries one after another, with no rule between them, as every section lists them. */
@Composable
private fun SourceList(sources: List<Source>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (source in sources) SourceEntry(source)
    }
}

/**
 * The SIL Open Font License, in full, folded away until asked for. Shipping the file in the
 * app is not enough on its own: the licence asks that it can be read.
 */
@Composable
private fun FontLicence() {
    var open by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Back by the action's own touch padding, so the word lines up with the entries above.
        Box(Modifier.offset(x = (-6).dp)) {
            TextAction(if (open) "Hide the font licence" else "Read the font licence") { open = !open }
        }
        if (open) {
            val text = remember { reflowed(resources.openRawResource(R.raw.ofl).bufferedReader().use { it.readText() }) }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Plain-text licence paragraphs as a phone shows them: the file is wrapped for a terminal,
 * which on a narrow screen breaks every line halfway across. Paragraphs stay apart and the
 * lines inside one run together, except a heading (a short line in capitals), which keeps a
 * line of its own; the rules of dashes drawn around headings go.
 */
private fun reflowed(text: String): String =
    text.replace("\r\n", "\n").trim().split(Regex("\n\\s*\n")).joinToString("\n\n") { paragraph ->
        val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.all { c -> c == '-' } }
        val heading = lines.firstOrNull()?.takeIf { it.length < 40 && it.none(Char::isLowerCase) && lines.size > 1 }
        val body = (if (heading != null) lines.drop(1) else lines).joinToString(" ")
        if (heading != null) "$heading\n$body" else body
    }

@Composable
private fun SourceEntry(source: Source) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(source.name, style = MaterialTheme.typography.titleSmall)
        Text(
            source.usedFor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            listOfNotNull(source.credit, source.licence).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A plain clickable Text rather than a TextButton: long URLs have to wrap onto a
        // second line, which a button's fixed label bounds clip.
        Text(
            source.url,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { uriHandler.openUri(source.url) }
                .padding(vertical = 6.dp),
        )
    }
}
