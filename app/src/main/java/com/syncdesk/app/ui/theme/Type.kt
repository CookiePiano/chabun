package com.syncdesk.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.syncdesk.app.R

/**
 * NeoHyundai in the three weights the design uses: R (400), B (700) and EB (800, only for the
 * power number). Each weight adds about 1.4 MB to the app.
 */
val NeoHyundai = FontFamily(
    Font(R.font.neohyundai_regular, FontWeight.Normal),
    Font(R.font.neohyundai_bold, FontWeight.Bold),
    Font(R.font.neohyundai_extrabold, FontWeight.ExtraBold),
)

/**
 * Lines break between phrases instead of inside a word (CSS `word-break: keep-all`), which is
 * how Korean reads best. Takes effect on Android 13 and later.
 */
private val PhraseBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = NeoHyundai,
    lineBreak = PhraseBreak,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/** Fixed-width digits, for numbers that change in place (power, battery, call time). */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * The nine type steps of spec section 4 (review item 6), in sp. The emulator runs at 160 dpi, so
 * 1 sp is one pixel of the 1682 x 1210 design. The smallest step is 24.
 */
@Immutable
class SyncDeskType {
    /** 68 / 84 B: the first-use title "차분을 시작합니다". */
    val display1 = style(68, 84, FontWeight.Bold, tracking = -0.015f)

    /** 56 / 72 B: the title of an empty screen. */
    val display2 = style(56, 72, FontWeight.Bold, tracking = -0.01f)

    /** 44 / 56 B: office side-panel titles; the small window's one line at a third of the width. */
    val title1 = style(44, 56, FontWeight.Bold, tracking = -0.01f)

    /** 44 / 60 B: the small window's one line at a third of the width (spec 5 gives it 60). */
    val title1Drive = style(44, 60, FontWeight.Bold, tracking = -0.01f)

    /** 52 / 70 B: the small window's one line at half width. */
    val title1Wide = style(52, 70, FontWeight.Bold, tracking = -0.01f)

    /** 40 / 58 B, tracking -1 %: the three key-summary lines after parking, Gleo's missed sentence. */
    val title2 = style(40, 58, FontWeight.Bold, tracking = -0.01f)

    /** 36 / 52 B: Gleo's new turn, sheet titles, the caller's name. */
    val bodyLarge = style(36, 52, FontWeight.Bold, tracking = -0.01f)

    /** 36 / 52 R: Gleo's question. */
    val bodyLargeRegular = style(36, 52, FontWeight.Normal)

    /** 30 / 44 R: missed sentences, setting rows. */
    val body = style(30, 44, FontWeight.Normal)

    /** 30 / 44 B: names in the call list, Work | Meeting. */
    val bodyStrong = style(30, 44, FontWeight.Bold)

    /** 32 B: every big button. Its line height is the button's, so only the size matters. */
    val button = style(32, 40, FontWeight.Bold)

    /** 28 / 40 R: earlier turns, list values. */
    val captionLarge = style(28, 40, FontWeight.Normal)

    /** 26 / 36 R: descriptions, times. */
    val caption = style(26, 36, FontWeight.Normal)

    /** 24 / 32 R: tick times, small lines. */
    val small = style(24, 32, FontWeight.Normal)

    /** 24 / 32 B: tags such as "내 약속" and "새 메모". */
    val smallStrong = style(24, 32, FontWeight.Bold)

    /** 44 / 52 EB, tracking -2 %, fixed-width digits: the power number "140W". */
    val power = style(44, 52, FontWeight.ExtraBold, tracking = -0.02f).tabularNumbers()

    // The styles the screens used before the redesign, mapped onto the new steps. Screen agents
    // move off them; they go in the clean-up stage.
    @Deprecated("Use display2", ReplaceWith("display2"))
    val status get() = display2

    @Deprecated("Use bodyLarge", ReplaceWith("bodyLarge"))
    val title get() = bodyLarge

    @Deprecated("Use caption", ReplaceWith("caption"))
    val label get() = caption

    @Deprecated("Use button", ReplaceWith("button"))
    val action get() = button
}
