package com.japanesedrills.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.japanesedrills.quiz.Primer
import com.japanesedrills.quiz.PrimerBlock
import com.japanesedrills.quiz.PrimerSection
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.ui.components.FuriganaText
import com.japanesedrills.ui.components.RichTable
import com.japanesedrills.ui.components.RichText
import com.japanesedrills.ui.components.SectionCard

/**
 * The conjugation primer: one page, read top to bottom. It is the only screen in the app
 * meant to be read rather than used, which is why it is a page of its own rather than a
 * long entry in the form list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(Primer.TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back to the form list")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Primer.SECTIONS, key = { it.title }) { section ->
                PrimerCard(section)
            }
        }
    }
}

@Composable
private fun PrimerCard(section: PrimerSection) {
    SectionCard(section.title) {
        for (block in section.blocks) {
            when (block) {
                is PrimerBlock.Line -> FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium)

                // On the text's baseline, or a reading over the first line lifts the bullet above it.
                is PrimerBlock.Bullet -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
                    FuriganaText(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alignByBaseline())
                }

                is PrimerBlock.Sub -> FuriganaText(
                    block.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is PrimerBlock.Step -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (block.note.isNotEmpty()) {
                        Text(
                            block.note,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RichText(
                        listOf(RichPart.Jp(block.from), RichPart.Text("  →  "), RichPart.Jp(block.to)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                is PrimerBlock.Table -> RichTable(
                    rows = block.rows,
                    header = block.header,
                )
            }
        }
    }
}
