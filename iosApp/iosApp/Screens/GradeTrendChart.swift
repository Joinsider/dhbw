// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Charts
import Shared

/// The semester averages as a line, oldest semester on the left.
///
/// German grades run the other way round from a chart's habit: 1,0 is the best, so the y-axis is
/// reversed and the good end sits at the top. The overall average is a quiet grey rule across the
/// plot; the accent carries only the series itself, so the eye reads the line first.
///
/// Labels come from `GradeTruncation`, the same cut the summary and the semester headers use, so a
/// point on the chart can never read differently from the number beside the list.
struct GradeTrendChart: View {

    let points: [SemesterAverage]
    let overall: Double?

    @Environment(\.accentChoice) private var accentChoice
    @State private var selected: String?

    private var accent: Color { accentChoice.accent }

    private var selectedPoint: SemesterAverage? {
        guard let selected else { return nil }
        return points.first { $0.semesterName == selected }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("grades.trend.title")
                .font(.headline)
            chart
                .frame(height: 180)
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .contain)
    }

    private var chart: some View {
        Chart {
            ForEach(points, id: \.semesterName) { point in
                LineMark(
                    x: .value("Semester", point.semesterName),
                    y: .value("Average", point.average)
                )
                .interpolationMethod(.linear)
                .lineStyle(StrokeStyle(lineWidth: 2))
                .foregroundStyle(accent)
            }

            // A ring in the card colour under each point, so the dot stays legible where the line
            // crosses or runs along a gridline.
            ForEach(points, id: \.semesterName) { point in
                PointMark(
                    x: .value("Semester", point.semesterName),
                    y: .value("Average", point.average)
                )
                .symbolSize(110)
                .foregroundStyle(Color.platformSecondaryGroupedBackground)
                // Decoration: VoiceOver reads each semester once, from the dot above.
                .accessibilityHidden(true)
            }

            ForEach(points, id: \.semesterName) { point in
                PointMark(
                    x: .value("Semester", point.semesterName),
                    y: .value("Average", point.average)
                )
                .symbolSize(60)
                .foregroundStyle(accent)
                .accessibilityLabel(Text(verbatim: point.semesterName))
                .accessibilityValue(Text(verbatim: GradeTrendChart.label(point.average)))
            }

            if let overall {
                RuleMark(y: .value("Overall", overall))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(.secondary)
                    .annotation(position: .top, alignment: .trailing, spacing: 2) {
                        Text(verbatim: "\(String(localized: "grades.trend.overall")) \(GradeTrendChart.label(overall))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
            }

            if let selectedPoint {
                RuleMark(x: .value("Semester", selectedPoint.semesterName))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(.secondary.opacity(0.5))
                    .annotation(
                        position: .top,
                        spacing: 4,
                        overflowResolution: .init(x: .fit(to: .chart), y: .disabled)
                    ) {
                        VStack(spacing: 1) {
                            Text(verbatim: selectedPoint.semesterName)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Text(verbatim: GradeTrendChart.label(selectedPoint.average))
                                .font(.caption.weight(.semibold))
                                .monospacedDigit()
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.platformSecondaryGroupedBackground, in: .rect(cornerRadius: 6))
                        .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                    }
            }
        }
        .chartYScale(domain: yDomain)
        .chartYAxis {
            AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                    .foregroundStyle(.secondary.opacity(0.3))
                AxisValueLabel(format: FloatingPointFormatStyle<Double>().precision(.fractionLength(1)))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .chartXAxis {
            AxisMarks { value in
                AxisValueLabel {
                    if let name = value.as(String.self) {
                        Text(verbatim: GradeTrendChart.shortName(name))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .chartXSelection(value: $selected)
        .chartLegend(.hidden)
    }

    /// Inverted (1,0 at the top), padded by 0,2 on either side and kept inside the German scale.
    /// Automatic so the domain follows the data, including the overall rule; the padding is applied
    /// to the inferred bounds rather than hard-coded, so a single outlier does not flatten the line.
    private var yDomain: AutomaticScaleDomain {
        .automatic(includesZero: false, reversed: true, dataType: Double.self) { values in
            guard let low = values.min(), let high = values.max() else { return }
            values = [
                min(max(low - 0.2, 1.0), 5.0),
                min(max(high + 0.2, 1.0), 5.0),
            ]
        }
    }

    // MARK: - Formatting

    /// The DHBW cut to one decimal, with the locale's decimal separator (`2,5` in German).
    static func label(_ average: Double) -> String {
        GradeTruncation.shared.oneDecimal(average: average)
            .formatted(.number.precision(.fractionLength(1)))
    }

    /// `WiSe 2025/26` becomes `WS 25/26` and `SoSe 2026` becomes `SS 26`, so the labels fit beside
    /// each other. Anything else is returned unchanged.
    static func shortName(_ full: String) -> String {
        let parts = full.split(separator: " ", maxSplits: 1)
        guard parts.count == 2 else { return full }
        let term: String
        switch String(parts[0]) {
        case "WiSe": term = "WS"
        case "SoSe": term = "SS"
        default: return full
        }
        let years = parts[1]
            .split(separator: "/")
            .map { year -> String in year.count == 4 ? String(year.suffix(2)) : String(year) }
            .joined(separator: "/")
        return "\(term) \(years)"
    }
}
