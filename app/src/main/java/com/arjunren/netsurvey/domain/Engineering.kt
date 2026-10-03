package com.arjunren.netsurvey.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class WifiBand(val label: String) { GHZ_2_4("2.4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz"), UNKNOWN("Unknown") }
enum class SignalQuality { EXCELLENT, GOOD, ACCEPTABLE, WEAK, POOR }

data class RssiThresholds(
    val excellentMin: Int = -55,
    val goodMin: Int = -67,
    val acceptableMin: Int = -72,
    val weakMin: Int = -80,
) {
    init {
        require(excellentMin > goodMin && goodMin > acceptableMin && acceptableMin > weakMin)
    }

    fun classify(rssi: Int): SignalQuality = when {
        rssi >= excellentMin -> SignalQuality.EXCELLENT
        rssi >= goodMin -> SignalQuality.GOOD
        rssi >= acceptableMin -> SignalQuality.ACCEPTABLE
        rssi >= weakMin -> SignalQuality.WEAK
        else -> SignalQuality.POOR
    }
}

object WifiChannels {
    fun band(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
        in 2400..2500 -> WifiBand.GHZ_2_4
        in 4900..5895 -> WifiBand.GHZ_5
        in 5925..7125 -> WifiBand.GHZ_6
        else -> WifiBand.UNKNOWN
    }

    fun channel(frequencyMhz: Int): Int? = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 && (frequencyMhz - 2407) % 5 == 0 -> (frequencyMhz - 2407) / 5
        frequencyMhz == 5935 -> 2
        frequencyMhz in 5000..5895 && (frequencyMhz - 5000) % 5 == 0 -> (frequencyMhz - 5000) / 5
        frequencyMhz in 5955..7115 && (frequencyMhz - 5950) % 5 == 0 -> (frequencyMhz - 5950) / 5
        else -> null
    }
}

data class RollingStatistics(val current: Double, val average: Double, val minimum: Int, val maximum: Int, val count: Int, val standardDeviation: Double)

fun rollingStatistics(samples: List<Int>): RollingStatistics? {
    if (samples.isEmpty()) return null
    val average = samples.average()
    val variance = samples.sumOf { (it - average).pow(2) } / samples.size
    return RollingStatistics(samples.last().toDouble(), average, samples.min(), samples.max(), samples.size, sqrt(variance))
}

data class Measurement(val x: Double, val y: Double, val rssi: Double)
data class HeatCell(val x: Double, val y: Double, val rssi: Double)

object IdwInterpolator {
    fun interpolate(
        measurements: List<Measurement>,
        columns: Int,
        rows: Int,
        power: Double = 2.0,
    ): List<HeatCell> {
        require(columns in 2..200 && rows in 2..200) { "Heatmap grid must be bounded" }
        require(power > 0)
        if (measurements.isEmpty()) return emptyList()
        return buildList(columns * rows) {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val x = column.toDouble() / (columns - 1)
                    val y = row.toDouble() / (rows - 1)
                    val exact = measurements.firstOrNull { abs(it.x - x) < 1e-9 && abs(it.y - y) < 1e-9 }
                    val value = exact?.rssi ?: run {
                        var weighted = 0.0
                        var totalWeight = 0.0
                        measurements.forEach { point ->
                            val distance = sqrt((point.x - x).pow(2) + (point.y - y).pow(2)).coerceAtLeast(1e-6)
                            val weight = 1.0 / distance.pow(power)
                            weighted += point.rssi * weight
                            totalWeight += weight
                        }
                        weighted / totalWeight
                    }
                    add(HeatCell(x, y, value))
                }
            }
        }
    }
}

data class Point2d(val x: Double, val y: Double)
data class WallSegment(val start: Point2d, val end: Point2d, val attenuationDb: Double)

fun segmentsIntersect(a: Point2d, b: Point2d, c: Point2d, d: Point2d): Boolean {
    fun orientation(p: Point2d, q: Point2d, r: Point2d): Int {
        val value = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
        return when { abs(value) < 1e-10 -> 0; value > 0 -> 1; else -> 2 }
    }
    fun onSegment(p: Point2d, q: Point2d, r: Point2d) =
        q.x in minOf(p.x, r.x)..maxOf(p.x, r.x) && q.y in minOf(p.y, r.y)..maxOf(p.y, r.y)
    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    if (o1 != o2 && o3 != o4) return true
    return (o1 == 0 && onSegment(a, c, b)) || (o2 == 0 && onSegment(a, d, b)) ||
        (o3 == 0 && onSegment(c, a, d)) || (o4 == 0 && onSegment(c, b, d))
}

fun predictedRssi(
    transmitPowerDbm: Double,
    frequencyMhz: Int,
    distanceMeters: Double,
    pathLossExponent: Double = 2.4,
    wallLossDb: Double = 0.0,
): Double {
    require(distanceMeters > 0 && frequencyMhz > 0 && pathLossExponent > 0)
    val referenceLossAtOneMeter = 20 * log10(frequencyMhz.toDouble()) - 27.55
    return transmitPowerDbm - referenceLossAtOneMeter - 10 * pathLossExponent * log10(distanceMeters.coerceAtLeast(1.0)) - wallLossDb
}

data class CandidateScore(val location: Point2d, val score: Double, val weakPointsCovered: Int, val wallIntersections: Int)

fun scoreCandidate(
    location: Point2d,
    weakPoints: List<Point2d>,
    walls: List<WallSegment>,
    maxDistanceNormalized: Double = 0.35,
): CandidateScore {
    var intersections = 0
    var covered = 0
    var score = 0.0
    weakPoints.forEach { point ->
        val distance = sqrt((point.x - location.x).pow(2) + (point.y - location.y).pow(2))
        val wallPenalty = walls.filter { segmentsIntersect(location, point, it.start, it.end) }.sumOf { it.attenuationDb }
        intersections += walls.count { segmentsIntersect(location, point, it.start, it.end) }
        if (distance <= maxDistanceNormalized) covered++
        score += 1.0 / (distance + 0.05) - wallPenalty / 20.0
    }
    return CandidateScore(location, score, covered, intersections)
}

fun applyCalibration(rawRssi: Int, offsetDb: Int): Int = rawRssi + offsetDb

fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (value.any { it == ',' || it == '\"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

fun isSafeZipEntry(name: String): Boolean {
    if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
    val normalized = name.replace('\\', '/')
    return normalized.split('/').none { it == ".." } && !Regex("^[A-Za-z]:").containsMatchIn(normalized)
}

fun shareMime(extension: String): String = when (extension.lowercase(Locale.US).removePrefix(".")) {
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "zip", "netsurvey" -> "application/zip"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}
