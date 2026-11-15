package com.syncdesk.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colour roles of spec section 2 for the current day or night, plus the always-dark stage. */
@Immutable
data class SyncDeskColors(
    val ground: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val glass: Color,
    val glassLine: Color,
    val glassStrong: Color,
    val chipOn: Color,
    val solid: Color,
    val onSolid: Color,
    val missBar: Color,
    val missTint: Color,
    val missInk: Color,
    val marker: Color,
    val wave: Color,
    val gleo: Color,
    val glow1: Color,
    val glow2: Color,
    val nav: Color,
    val road: Color,
    val route: Color,
    /** The office and first-use car stage, dark in both themes. */
    val stageInk: Color,
    val stageMuted: Color,
    val stageAccent: Color,
    val stageLine: Color,
    val warmLight: Color,
    val isNight: Boolean,
) {
    // The roles the screens used before the redesign, mapped as spec 2 says (end of the section).
    // Screen agents move off them; they go in the clean-up stage.
    @Deprecated("Use ink", ReplaceWith("ink"))
    val text: Color get() = ink

    @Deprecated("Use muted", ReplaceWith("muted"))
    val textMuted: Color get() = muted

    @Deprecated("Use accent", ReplaceWith("accent"))
    val signal: Color get() = accent

    @Deprecated("Use missInk", ReplaceWith("missInk"))
    val caution: Color get() = missInk

    @Deprecated("Not in the new design; use accent", ReplaceWith("accent"))
    val lamp: Color get() = accent

    @Deprecated("Not in the new design; use ground", ReplaceWith("ground"))
    val lampGlow: Color get() = ground

    @Deprecated("Use glassStrong", ReplaceWith("glassStrong"))
    val surface: Color get() = glassStrong

    @Deprecated("Use glassLine", ReplaceWith("glassLine"))
    val line: Color get() = glassLine

    @Deprecated("Use muted", ReplaceWith("muted"))
    val outline: Color get() = muted
}

private fun PaletteTokens.toColors(isNight: Boolean): SyncDeskColors {
    val stage = Palette.stage
    return SyncDeskColors(
        ground = Color(ground),
        ink = Color(ink),
        muted = Color(muted),
        accent = Color(accent),
        onAccent = Color(onAccent),
        glass = Color(glass),
        glassLine = Color(glassLine),
        glassStrong = Color(glassStrong),
        chipOn = Color(chipOn),
        solid = Color(solid),
        onSolid = Color(onSolid),
        missBar = Color(missBar),
        missTint = Color(missTint),
        missInk = Color(missInk),
        marker = Color(marker),
        wave = Color(wave),
        gleo = Color(gleo),
        glow1 = Color(glow1),
        glow2 = Color(glow2),
        nav = Color(nav),
        road = Color(road),
        route = Color(route),
        stageInk = Color(stage.ink),
        stageMuted = Color(stage.muted),
        stageAccent = Color(stage.accent),
        stageLine = Color(stage.line),
        warmLight = Color(stage.warmLight),
        isNight = isNight,
    )
}

/**
 * Spacing the screens used before the redesign. The new screens take their sizes from [Design]
 * and the Figma frames instead. Goes in the clean-up stage.
 */
@Immutable
class SyncDeskDimens {
    val gutter: Dp = 56.dp
    val gapSmall: Dp = 12.dp
    val gap: Dp = 24.dp
    val gapLarge: Dp = 56.dp
    val touch: Dp = 96.dp
    val action: Dp = 112.dp
    val icon: Dp = 44.dp
    val radius: Dp = 20.dp
    val hairline: Dp = 2.dp
    val bar: Dp = 16.dp
}

private val LocalColors = staticCompositionLocalOf { Palette.night.toColors(isNight = true) }
private val LocalType = staticCompositionLocalOf { SyncDeskType() }
private val LocalDimens = staticCompositionLocalOf { SyncDeskDimens() }
private val LocalMotion = staticCompositionLocalOf { SyncDeskMotion(reduced = false) }

object SyncDesk {
    val colors: SyncDeskColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: SyncDeskType
        @Composable @ReadOnlyComposable get() = LocalType.current
    val motion: SyncDeskMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
    val shapes: SyncDeskShapes get() = SyncDeskShapes

    @Deprecated("Use Design and the sizes of the Figma frames")
    val dimens: SyncDeskDimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
}

/**
 * Whether the system lets the app animate: the animator duration scale is not 0 (developer
 * options, or the accessibility setting "애니메이션 삭제", which sets it to 0).
 */
@Composable
private fun systemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/**
 * Colours, type and motion for the whole app. Follows the vehicle's day and night mode (the
 * system UI mode) by default. With [motionEnabled] false, motion is reduced as spec 8-4 says.
 */
@Composable
fun SyncDeskTheme(
    night: Boolean = isSystemInDarkTheme(),
    motionEnabled: Boolean = systemAnimationsEnabled(),
    content: @Composable () -> Unit,
) {
    val colors = remember(night) { (if (night) Palette.night else Palette.day).toColors(night) }
    val motion = remember(motionEnabled) { SyncDeskMotion(reduced = !motionEnabled) }
    // Material components (ripple, text selection) take their colours from the same roles.
    val scheme = if (night) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.ground,
            onSurface = colors.ink,
            outline = colors.muted,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.ground,
            onSurface = colors.ink,
            outline = colors.muted,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalMotion provides motion,
            content = content,
        )
    }
}
