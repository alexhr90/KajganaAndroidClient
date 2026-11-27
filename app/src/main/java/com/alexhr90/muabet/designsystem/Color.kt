package com.alexhr90.muabet.designsystem

import androidx.compose.ui.graphics.Color

// Muabet uses Izvor's tokens with one change: the accent is Kajgana orange instead of lime and
// olive (SPEC §5). Neutrals, warning and negative tones are Izvor's. Screens never use these
// directly; they go through MaterialTheme and MaterialTheme.extended.

// Dark: charcoal and Kajgana orange
val Charcoal = Color(0xFF101112)
val Ivory = Color(0xFFEDEEE7)
val Sage = Color(0xFFA6ABA4)
val Orange = Color(0xFFFF6D10)
val OrangeContainerDark = Color(0xFF3A2008)
val DividerDark = Color(0xFF30342F)
val WarningDark = Color(0xFFFFCB66)
val NegativeDark = Color(0xFFFF8D79)

// Light: paper and burnt orange (the bright orange is only 2.6:1 on paper)
val Paper = Color(0xFFF5F6F0)
val Ink = Color(0xFF101112)
val Moss = Color(0xFF5B6158)
val BurntOrange = Color(0xFFA34300)
val OrangeContainerLight = Color(0xFFFFE0C9)
val OnOrangeContainerLight = Color(0xFF421B00)
val DividerLight = Color(0xFFD4D8CE)
val WarningLight = Color(0xFF815300)
val NegativeLight = Color(0xFFA32F20)
