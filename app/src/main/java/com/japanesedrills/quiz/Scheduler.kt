package com.japanesedrills.quiz

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * When an item is next due, in local epoch days.
 *
 * [step] indexes [Scheduler.LADDER]; -1 means the item has been met but never answered
 * correctly. [ease] stretches or compresses the ladder for this particular item.
 */
data class SrsState(
    val step: Int = -1,
    val ease: Double = 1.0,
    val due: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/**
 * A fixed interval ladder with a per-item ease multiplier: SM-2 without the parameters
 * nobody can tune. It is deliberately not FSRS — that wants a training set this app will
 * never have.
 *
 * Pure by design: "today" is always passed in, so every branch is testable and no clock
 * reaches this file.
 */
object Scheduler {

    /** Days between reviews at each step. */
    val LADDER = intArrayOf(1, 3, 7, 16, 35, 90)

    /** [SrsState.step] for an item that has been met but never answered correctly. */
    const val UNLEARNED = -1

    private const val EASE_MIN = 0.6
    private const val EASE_MAX = 1.4
    private const val EASE_UP = 1.05
    private const val EASE_DOWN = 0.85

    /** Nothing is ever scheduled further out than this, so a clock jump cannot bury an item. */
    private const val MAX_INTERVAL = 365L

    fun review(state: SrsState, correct: Boolean, today: Long): SrsState = when {
        // A right answer before the item is due says nothing the schedule does not already
        // know. A session asks the same skill a dozen times running, and counting each one
        // climbed the whole ladder in an afternoon. The first right answer still starts it.
        correct && state.step > UNLEARNED && !isDue(state, today) -> state.copy(reps = state.reps + 1)
        correct -> {
            val step = min(state.step + 1, LADDER.size - 1)
            val ease = min(state.ease * EASE_UP, EASE_MAX)
            state.copy(
                step = step,
                ease = ease,
                due = today + interval(step, ease),
                reps = state.reps + 1,
            )
        }
        // A lapse steps back one rung rather than resetting to zero: a single slip on a
        // well-known item is not evidence that it was never learned, and a hard reset is
        // the fastest way to make a review queue feel punitive.
        //
        // An item that has never been answered correctly stays below the ladder instead,
        // otherwise getting it wrong first time would leave it in the same state as
        // getting it right and would show up as mastery it has not earned.
        else -> state.copy(
            step = if (state.step <= UNLEARNED) UNLEARNED else max(state.step - 1, 0),
            ease = max(state.ease * EASE_DOWN, EASE_MIN),
            due = today + 1,
            reps = state.reps + 1,
            lapses = state.lapses + 1,
        )
    }

    fun isDue(state: SrsState, today: Long): Boolean = state.due <= today

    /** How far through the ladder this item is, for the rings on the path. 0f..1f. */
    fun strength(state: SrsState): Float =
        ((state.step + 1).toFloat() / LADDER.size).coerceIn(0f, 1f)

    private fun interval(step: Int, ease: Double): Long =
        (LADDER[step] * ease).roundToLong().coerceIn(1L, MAX_INTERVAL)
}
