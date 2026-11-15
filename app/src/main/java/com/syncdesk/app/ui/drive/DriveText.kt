package com.syncdesk.app.ui.drive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * Wraps at spaces using the rendered font and width, keeping explicit newlines and blank lines.
 * A word wider than the window stays intact; the caller handles overflow with softWrap disabled.
 * Callers expose the original text to accessibility rather than these layout-only line breaks.
 */
@Composable
internal fun rememberWordWrappedText(text: String, style: TextStyle, maxWidth: Dp, balance: Boolean = false): String {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val availableWidth = if (maxWidth.value.isFinite()) {
        with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)
    } else {
        Int.MAX_VALUE
    }
    return remember(text, style, availableWidth, measurer, density, balance) {
        fun measuredWidth(value: String) = measurer.measure(
            text = AnnotatedString(value),
            style = style,
            softWrap = false,
            maxLines = 1,
        ).size.width
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            val paragraphLines = mutableListOf<String>()
            var line = ""
            paragraph.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && measuredWidth(candidate) > availableWidth) {
                    paragraphLines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            paragraphLines += line
            // Centered status headlines should not leave a short suffix alone on a line.
            if (balance && paragraphLines.size == 2) {
                val words = paragraph.split(' ')
                val balanced = (1 until words.size).mapNotNull { split ->
                    val first = words.take(split).joinToString(" ")
                    val second = words.drop(split).joinToString(" ")
                    val firstWidth = measuredWidth(first)
                    val secondWidth = measuredWidth(second)
                    if (maxOf(firstWidth, secondWidth) <= availableWidth) {
                        Triple(first, second, kotlin.math.abs(firstWidth - secondWidth))
                    } else null
                }.minByOrNull { it.third }
                if (balanced != null) {
                    paragraphLines[0] = balanced.first
                    paragraphLines[1] = balanced.second
                }
            }
            lines += paragraphLines
        }
        lines.joinToString("\n")
    }
}
