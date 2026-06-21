package com.example.data.model

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speedMps: Float = 0f,
    val timestamp: Long,
    val startsNewSegment: Boolean = false
)

data class GpsSessionMetrics(
    val distanceKm: Double,
    val durationSeconds: Long,
    val averagePaceMinPerKm: Double,
    val averageSpeedKmh: Double,
    val currentSpeedKmh: Double = 0.0,
    val validPoints: Int = 0,
    val discardedPoints: Int = 0,
    val discardedSegments: Int = 0,
    val lastAccuracyMeters: Float = 0f,
    val signalQuality: GpsSignalQuality = GpsSignalQuality.NONE,
    val isGpsUnstable: Boolean = false
)

enum class GpsSignalQuality {
    NONE,
    GOOD,
    REGULAR,
    WEAK
}

object ActivityTrackingRules {
    private const val earthRadiusMeters = 6371000.0
    private const val runningMaxUsableAccuracyMeters = 30f
    private const val cyclingMaxUsableAccuracyMeters = 50f
    private const val runningMaxPlausibleSpeedKmh = 25.0
    private const val cyclingMaxPlausibleSpeedKmh = 70.0
    private const val walkingMaxPlausibleSpeedKmh = 12.0
    private const val runningMinMovingSpeedKmh = 0.3
    private const val cyclingMinMovingSpeedKmh = 0.5
    private const val walkingMinMovingSpeedKmh = 0.8
    private const val minSegmentDistanceMeters = 3.0
    private const val minDistancePoints = 3
    private const val minReliablePaceDistanceKm = 0.05
    private const val minReliablePaceDurationSeconds = 15L
    private const val minReliablePacePoints = 4

    fun isUsablePoint(
        point: GpsRoutePoint,
        discipline: UserDiscipline = UserDiscipline.RUNNING
    ): Boolean {
        return point.latitude in -90.0..90.0 &&
            point.longitude in -180.0..180.0 &&
            point.accuracy > 0f &&
            point.accuracy <= maxUsableAccuracyMeters(discipline)
    }

    fun summarize(
        points: List<GpsRoutePoint>,
        durationSeconds: Long,
        discipline: UserDiscipline = UserDiscipline.RUNNING
    ): GpsSessionMetrics {
        val cleanPoints = filterUsableRoutePoints(points, discipline)
        val usableCandidates = points.filter { isUsablePoint(it, discipline) }
        val discardedPoints = points.size - usableCandidates.size
        val discardedSegments = (usableCandidates.size - cleanPoints.size).coerceAtLeast(0)
        val meters = if (cleanPoints.size >= minDistancePoints) {
            cleanPoints.zipWithNext().sumOf { (start, end) ->
                if (end.startsNewSegment) {
                    0.0
                } else {
                    distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
                }
            }
        } else {
            0.0
        }
        val distanceKm = meters / 1000.0
        val hours = durationSeconds.coerceAtLeast(0) / 3600.0
        val minutes = durationSeconds.coerceAtLeast(0) / 60.0
        val rawPace = if (distanceKm > 0.0) minutes / distanceKm else 0.0
        val signalQuality = signalQualityForAccuracy(cleanPoints.lastOrNull()?.accuracy)
        val isGpsUnstable = discardedSegments >= 1 || discardedPoints >= 2 || signalQuality == GpsSignalQuality.WEAK
        val shouldHideAbsurdRunningPace = discipline == UserDiscipline.RUNNING &&
            rawPace in 0.0..<3.0 &&
            isGpsUnstable
        return GpsSessionMetrics(
            distanceKm = distanceKm,
            durationSeconds = durationSeconds.coerceAtLeast(0),
            averagePaceMinPerKm = if (canCalculateAveragePace(cleanPoints, distanceKm, durationSeconds)) {
                if (shouldHideAbsurdRunningPace) 0.0 else rawPace
            } else {
                0.0
            },
            averageSpeedKmh = if (distanceKm > 0.0 && hours > 0.0) distanceKm / hours else 0.0,
            currentSpeedKmh = currentSegmentSpeedKmh(cleanPoints, discipline),
            validPoints = cleanPoints.size,
            discardedPoints = discardedPoints,
            discardedSegments = discardedSegments,
            lastAccuracyMeters = cleanPoints.lastOrNull()?.accuracy ?: 0f,
            signalQuality = signalQuality,
            isGpsUnstable = isGpsUnstable
        )
    }

    fun filterUsableRoutePoints(
        points: List<GpsRoutePoint>,
        discipline: UserDiscipline = UserDiscipline.RUNNING
    ): List<GpsRoutePoint> {
        val accepted = mutableListOf<GpsRoutePoint>()
        points.filter { isUsablePoint(it, discipline) }.forEach { point ->
            val previous = accepted.lastOrNull()
            if (previous == null || point.startsNewSegment || isPlausibleNextPoint(previous, point, discipline)) {
                accepted += point
            }
        }
        return accepted
    }

    fun isPlausibleNextPoint(
        previous: GpsRoutePoint,
        next: GpsRoutePoint,
        discipline: UserDiscipline = UserDiscipline.RUNNING
    ): Boolean {
        if (!isUsablePoint(previous, discipline) || !isUsablePoint(next, discipline)) return false
        if (next.startsNewSegment) return true
        val meters = distanceMeters(previous.latitude, previous.longitude, next.latitude, next.longitude)
        
        val baseMinDistance = when (discipline) {
            UserDiscipline.RUNNING -> 10.0
            UserDiscipline.CYCLING -> 15.0
            UserDiscipline.WALKING -> 8.0
            else -> 10.0
        }
        val accuracyMargin = (previous.accuracy + next.accuracy).toDouble() * 0.4
        val effectiveMinDistance = maxOf(baseMinDistance, accuracyMargin)
        
        if (meters < effectiveMinDistance) return false
        val elapsedSeconds = (next.timestamp - previous.timestamp) / 1000.0
        if (elapsedSeconds <= 0.0) return false
        val speedKmh = (meters / elapsedSeconds) * 3.6
        return speedKmh in minMovingSpeedKmh(discipline)..maxPlausibleSpeedKmh(discipline)
    }

    private fun canCalculateAveragePace(
        cleanPoints: List<GpsRoutePoint>,
        distanceKm: Double,
        durationSeconds: Long
    ): Boolean {
        return cleanPoints.size >= minReliablePacePoints &&
            distanceKm >= minReliablePaceDistanceKm &&
            durationSeconds >= minReliablePaceDurationSeconds
    }

    private fun currentSegmentSpeedKmh(
        cleanPoints: List<GpsRoutePoint>,
        discipline: UserDiscipline
    ): Double {
        val lastPair = cleanPoints.zipWithNext().lastOrNull { (_, end) -> !end.startsNewSegment } ?: return 0.0
        val (previous, next) = lastPair
        if (!isPlausibleNextPoint(previous, next, discipline)) return 0.0
        val elapsedSeconds = (next.timestamp - previous.timestamp) / 1000.0
        if (elapsedSeconds <= 0.0) return 0.0
        val meters = distanceMeters(previous.latitude, previous.longitude, next.latitude, next.longitude)
        return (meters / elapsedSeconds) * 3.6
    }

    private fun maxUsableAccuracyMeters(discipline: UserDiscipline): Float {
        return when (discipline) {
            UserDiscipline.CYCLING -> cyclingMaxUsableAccuracyMeters
            UserDiscipline.RUNNING, UserDiscipline.WALKING -> runningMaxUsableAccuracyMeters
            else -> runningMaxUsableAccuracyMeters
        }
    }

    private fun maxPlausibleSpeedKmh(discipline: UserDiscipline): Double {
        return when (discipline) {
            UserDiscipline.CYCLING -> cyclingMaxPlausibleSpeedKmh
            UserDiscipline.WALKING -> walkingMaxPlausibleSpeedKmh
            UserDiscipline.RUNNING -> runningMaxPlausibleSpeedKmh
            else -> runningMaxPlausibleSpeedKmh
        }
    }

    private fun minMovingSpeedKmh(discipline: UserDiscipline): Double {
        return when (discipline) {
            UserDiscipline.CYCLING -> cyclingMinMovingSpeedKmh
            UserDiscipline.WALKING -> walkingMinMovingSpeedKmh
            UserDiscipline.RUNNING -> runningMinMovingSpeedKmh
            else -> runningMinMovingSpeedKmh
        }
    }

    fun signalQualityForAccuracy(accuracy: Float?): GpsSignalQuality {
        return when {
            accuracy == null || accuracy <= 0f -> GpsSignalQuality.NONE
            accuracy <= 10f -> GpsSignalQuality.GOOD
            accuracy <= 30f -> GpsSignalQuality.REGULAR
            else -> GpsSignalQuality.WEAK
        }
    }

    fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val dLat = Math.toRadians(endLatitude - startLatitude)
        val dLon = Math.toRadians(endLongitude - startLongitude)
        val lat1 = Math.toRadians(startLatitude)
        val lat2 = Math.toRadians(endLatitude)
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun toRouteJson(points: List<GpsRoutePoint>): String {
        if (points.isEmpty()) return "[]"
        return points.joinToString(prefix = "[", postfix = "]") { point ->
            String.format(
                Locale.US,
                "{\"lat\":%.7f,\"lng\":%.7f,\"alt\":%.2f,\"acc\":%.1f,\"speed\":%.2f,\"ts\":%d,\"seg\":%s}",
                point.latitude,
                point.longitude,
                point.altitude,
                point.accuracy,
                point.speedMps,
                point.timestamp,
                point.startsNewSegment.toString()
            )
        }
    }

    fun toRouteGeoJson(
        points: List<GpsRoutePoint>,
        discipline: UserDiscipline = UserDiscipline.RUNNING
    ): String {
        val segments = splitRouteSegments(filterUsableRoutePoints(points, discipline))
        if (segments.isEmpty()) {
            return "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[]},\"properties\":{}}"
        }
        if (segments.size == 1) {
            val coordinates = segments.first()
                .joinToString(prefix = "[", postfix = "]", separator = ",") { point ->
                    String.format(Locale.US, "[%.7f,%.7f]", point.longitude, point.latitude)
                }
            return "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":$coordinates},\"properties\":{}}"
        }
        val coordinates = segments.joinToString(prefix = "[", postfix = "]", separator = ",") { segment ->
            segment.joinToString(prefix = "[", postfix = "]", separator = ",") { point ->
                String.format(Locale.US, "[%.7f,%.7f]", point.longitude, point.latitude)
            }
        }
        return "{\"type\":\"Feature\",\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":$coordinates},\"properties\":{}}"
    }

    fun fromRouteJson(routeJson: String): List<GpsRoutePoint> {
        if (routeJson.isBlank() || routeJson == "[]") return emptyList()
        val regex = Regex(
            "\\{\"lat\":(-?\\d+(?:\\.\\d+)?),\"lng\":(-?\\d+(?:\\.\\d+)?),\"alt\":(-?\\d+(?:\\.\\d+)?),\"acc\":(-?\\d+(?:\\.\\d+)?),\"speed\":(-?\\d+(?:\\.\\d+)?),\"ts\":(\\d+)(?:,\"seg\":(true|false))?\\}"
        )
        return regex.findAll(routeJson).mapNotNull { match ->
            val values = match.groupValues
            GpsRoutePoint(
                latitude = values[1].toDoubleOrNull() ?: return@mapNotNull null,
                longitude = values[2].toDoubleOrNull() ?: return@mapNotNull null,
                altitude = values[3].toDoubleOrNull() ?: 0.0,
                accuracy = values[4].toFloatOrNull() ?: 0f,
                speedMps = values[5].toFloatOrNull() ?: 0f,
                timestamp = values[6].toLongOrNull() ?: 0L,
                startsNewSegment = values.getOrNull(7)?.toBooleanStrictOrNull() ?: false
            )
        }.toList()
    }

    private fun splitRouteSegments(points: List<GpsRoutePoint>): List<List<GpsRoutePoint>> {
        val segments = mutableListOf<MutableList<GpsRoutePoint>>()
        points.forEach { point ->
            if (segments.isEmpty() || point.startsNewSegment) {
                segments += mutableListOf(point)
            } else {
                segments.last() += point
            }
        }
        return segments.filter { it.isNotEmpty() }
    }

    fun canSave(distanceKm: Double, durationSeconds: Long): Boolean {
        return distanceKm >= 0.02 && durationSeconds >= 10
    }
}
