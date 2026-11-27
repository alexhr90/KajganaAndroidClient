package com.alexhr90.muabet.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** Compact list rows (Settings → Row density). */
val LocalCompactRows = staticCompositionLocalOf { false }

/** Post images wait for a tap before they load (Settings → Load images on tap). */
val LocalImagesOnTap = staticCompositionLocalOf { false }
