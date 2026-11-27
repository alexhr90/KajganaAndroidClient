package com.alexhr90.muabet.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** Durations from the family's motion tokens, in milliseconds. */
object MuabetMotion {
    const val PRESS = 120
    const val NAVIGATION = 200
    const val SHEET = 240
    const val MARK_REVEAL = 650
    const val EXIT = 200
}

/** True when the interface should not move. Reduced motion removes movement rather than slowing it. */
val LocalReducedMotion = staticCompositionLocalOf { false }
