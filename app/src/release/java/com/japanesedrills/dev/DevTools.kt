package com.japanesedrills.dev

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.japanesedrills.ui.DrillViewModel

/**
 * The release build's developer tools: none. The debug twin of this object (`src/debug`)
 * moves the clock, loads progress states and shortcuts sessions; here every entry point is
 * inert, so the shared code can call them without knowing which build it is in.
 */
object DevTools {

    val dayOffset: Long get() = 0

    @Suppress("UNUSED_PARAMETER")
    fun onLaunch(context: Context, intent: Intent?, fresh: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun SettingsSection(viewModel: DrillViewModel) = Unit

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun QuizActions(viewModel: DrillViewModel) = Unit
}
