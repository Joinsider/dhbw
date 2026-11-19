/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.grades.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.domain.model.GradeTruncation
import kotlin.math.roundToInt
import de.fampopprol.dhbwhorb.presentation.grades.SemesterAverage
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.grade_trend
import de.fampopprol.dhbwhorb.resources.grade_trend_overall
import org.jetbrains.compose.resources.stringResource

private const val MIN_GRADE = 1.0
private const val MAX_GRADE = 5.0
private const val DOMAIN_PADDING = 0.2

private val GutterLeft = 40.dp
private val GutterRight = 16.dp
private val GutterTop = 28.dp
private val GutterBottom = 24.dp
private val LineWidth = 2.dp
private val GridWidth = 0.5.dp
private val OverallWidth = 1.dp
private val DotRadius = 4.dp
private val RingWidth = 2.dp
private val LabelGap = 4.dp
private val ChartHeight = 180.dp

private val WinterSemester = Regex("""WiSe \d{2}(\d{2})/(\d{2})""")
private val SummerSemester = Regex("""SoSe \d{2}(\d{2})""")

/**
 * "WiSe 2025/26" becomes "WS 25/26" and "SoSe 2026" becomes "SS 26" so the x-axis labels fit
 * under the points. Any other name is returned unchanged.
 */
internal fun shortenSemesterLabel(name: String): String {
    WinterSemester.matchEntire(name)?.let { match ->
        return "WS ${match.groupValues[1]}/${match.groupValues[2]}"
    }
    SummerSemester.matchEntire(name)?.let { match ->
        return "SS ${match.groupValues[1]}"
    }
    return name
}

/** The grade range drawn on the y-axis: the data plus padding, clamped to 1.0..5.0. */
private fun trendDomain(averages: List<Double>, overallGpa: Double?): Pair<Double, Double> {
    val values = averages + listOfNotNull(overallGpa)
    if (values.isEmpty()) return MIN_GRADE to MAX_GRADE
    val low = (values.min() - DOMAIN_PADDING).coerceIn(MIN_GRADE, MAX_GRADE)
    val high = (values.max() + DOMAIN_PADDING).coerceIn(MIN_GRADE, MAX_GRADE)
    return if (high > low) low to high else MIN_GRADE to MAX_GRADE
}

/**
 * Where the gridlines go: on round grades, so each label names exactly the line beside it.
 *
 * Steps of 0,5 when the range holds at least two of them, otherwise 0,2. Spacing the lines evenly
 * across the domain instead put them on values like 1,93, labelled 1,9 after the DHBW cut — a
 * label that did not match its line.
 */
internal fun gridValues(low: Double, high: Double): List<Double> {
    for (step in listOf(0.5, 0.2)) {
        val first = kotlin.math.ceil(low / step - 1e-9).toInt()
        val last = kotlin.math.floor(high / step + 1e-9).toInt()
        if (last - first >= 1) return (first..last).map { it * step }
    }
    return listOf(low, high)
}

private class TrendColors(
    val primary: Color,
    val surface: Color,
    val outline: Color,
    val outlineVariant: Color,
    val labelStyle: TextStyle,
)

/**
 * Line chart of the semester averages, oldest semester on the left. German grades are better
 * when lower, so the y-axis is inverted: 1,0 sits at the top.
 */
@Composable
fun GradeTrendChart(
    points: List<SemesterAverage>,
    overallGpa: Double?,
    modifier: Modifier = Modifier
) {
    val title = stringResource(Res.string.grade_trend)
    val overallLabel = stringResource(Res.string.grade_trend_overall)
    val textMeasurer = rememberTextMeasurer()
    val colors = TrendColors(
        primary = MaterialTheme.colorScheme.primary,
        surface = CardDefaults.elevatedCardColors().containerColor,
        outline = MaterialTheme.colorScheme.outline,
        outlineVariant = MaterialTheme.colorScheme.outlineVariant,
        labelStyle = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
    )
    val description = remember(title, points) {
        title + ": " + points.joinToString(", ") {
            "${it.semesterName} ${GradeTruncation.oneDecimal(it.average)}"
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .semantics { contentDescription = description }
                    .testTag("gradeTrendChart")
            ) {
                drawTrend(points, overallGpa, overallLabel, colors, textMeasurer)
            }
        }
    }
}

private fun DrawScope.drawTrend(
    points: List<SemesterAverage>,
    overallGpa: Double?,
    overallLabel: String,
    colors: TrendColors,
    textMeasurer: TextMeasurer,
) {
    if (points.isEmpty()) return
    val (low, high) = trendDomain(points.map { it.average }, overallGpa)

    val left = GutterLeft.toPx()
    val right = size.width - GutterRight.toPx()
    val top = GutterTop.toPx()
    val bottom = size.height - GutterBottom.toPx()
    val plotHeight = bottom - top
    val plotWidth = right - left
    val labelGap = LabelGap.toPx()

    // Inverted: a lower grade is higher up the chart.
    fun yOf(grade: Double): Float =
        (top + (grade - low) / (high - low) * plotHeight).toFloat()

    // Recessive grid with one-decimal labels on the left.
    for (value in gridValues(low, high)) {
        val y = yOf(value)
        drawLine(
            color = colors.outlineVariant,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = GridWidth.toPx()
        )
        val layout = textMeasurer.measure(((value * 10).roundToInt() / 10.0).toString(), colors.labelStyle)
        drawText(
            layout,
            topLeft = Offset(
                left - labelGap - layout.size.width,
                y - layout.size.height / 2f
            )
        )
    }

    // The overall average as a horizontal reference line, labelled at its right end.
    overallGpa?.let { overall ->
        val y = yOf(overall)
        drawLine(
            color = colors.outline,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = OverallWidth.toPx()
        )
        val layout = textMeasurer.measure(
            "$overallLabel ${GradeTruncation.oneDecimal(overall)}",
            colors.labelStyle
        )
        drawText(
            layout,
            topLeft = Offset(
                right - layout.size.width,
                (y - layout.size.height - labelGap).coerceAtLeast(0f)
            )
        )
    }

    val coords = points.mapIndexed { index, point ->
        val x = if (points.size == 1) {
            left + plotWidth / 2f
        } else {
            left + plotWidth * index / (points.size - 1)
        }
        Offset(x, yOf(point.average))
    }

    val path = Path().apply {
        coords.forEachIndexed { index, offset ->
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
    }
    drawPath(
        path = path,
        color = colors.primary,
        style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Ring first in the card colour, so the dot stays legible where the line crosses it.
    coords.forEach { center ->
        drawCircle(
            color = colors.surface,
            radius = DotRadius.toPx() + RingWidth.toPx(),
            center = center
        )
        drawCircle(color = colors.primary, radius = DotRadius.toPx(), center = center)
    }

    // X-axis labels under each point. A label that would overlap the previous one is skipped.
    var previousRight = Float.NEGATIVE_INFINITY
    points.forEachIndexed { index, point ->
        val layout = textMeasurer.measure(shortenSemesterLabel(point.semesterName), colors.labelStyle)
        val x = (coords[index].x - layout.size.width / 2f)
            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        if (x >= previousRight + labelGap) {
            drawText(layout, topLeft = Offset(x, bottom + labelGap))
            previousRight = x + layout.size.width
        }
    }

    // Value of the first and last semester, directly above their dots.
    listOf(0, points.lastIndex).distinct().forEach { index ->
        val layout = textMeasurer.measure(
            GradeTruncation.oneDecimal(points[index].average).toString(),
            colors.labelStyle
        )
        val x = (coords[index].x - layout.size.width / 2f)
            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        val y = (coords[index].y - DotRadius.toPx() - RingWidth.toPx() - labelGap - layout.size.height)
            .coerceAtLeast(0f)
        drawText(layout, topLeft = Offset(x, y))
    }
}
