package com.example.shatterrealm.ui.theme

import androidx.compose.ui.graphics.Color

/** App-owned approved design colors; PicoTheme's native color roles remain intact. */
object RealmStyle {
    val smoke = Color(0xED222725) // design-style: fixed-figma-color design-spec tokens.brandColors.smoke; CSS RGBA converted to ARGB
    val porcelain = Color(0xFFF4F1E8) // design-style: fixed-figma-color tokens.brandColors.porcelain
    val muted = Color(0xFFB9C1BB) // design-style: fixed-figma-color tokens.brandColors.muted
    val accent = Color(0xFFB9E6C5) // design-style: fixed-figma-color tokens.brandColors.accent
    val warning = Color(0xFFE6C394) // design-style: fixed-figma-color tokens.brandColors.warning
    val pressed = Color(0xFF485A4D) // design-style: fixed-figma-color tokens.brandColors.pressed
    val room = Color(0xFFDAD6D3) // design-style: fixed-figma-color tokens.brandColors.room
    val Ink = smoke
    val Edge = pressed
    val Ivory = porcelain
    val Mint = accent
    val Tile = pressed
}
