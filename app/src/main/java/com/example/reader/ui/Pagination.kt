package com.example.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

@Composable
fun rememberPages(
    text: String,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<IntRange> {
    val measurer = rememberTextMeasurer()
    return remember(text, style, widthPx, heightPx) {
        paginate(text, measurer, style, widthPx, heightPx)
    }
}

private fun paginate(
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int
): List<IntRange> {
    val result = mutableListOf<IntRange>()
    if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) return result

    val constraints = Constraints(maxWidth = widthPx)
    var start = 0

    while (start < text.length && result.size < MAX_PAGES) {
        var low = start + 1
        var high = minOf(text.length, start + SAMPLE_CHARS)
        var best = start + 1

        while (low <= high) {
            val middle = (low + high) / 2
            val layout = measurer.measure(
                text = AnnotatedString(text.substring(start, middle)),
                style = style,
                constraints = constraints
            )
            if (layout.size.height <= heightPx) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        var end = best
        val paragraphBreak = text.lastIndexOf('\n', best - 1)
        if (paragraphBreak > start + (best - start) * 0.55) {
            end = paragraphBreak + 1
        }
        if (end <= start) end = minOf(text.length, start + 1)

        result.add(start until end)
        start = end
    }
    return result
}

private const val SAMPLE_CHARS = 6000
private const val MAX_PAGES = 5000
