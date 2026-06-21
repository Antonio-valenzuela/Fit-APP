package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTrackingRulesTest {
    @Test
    fun runningSessionUsesOnlyPlausibleDistanceAndPace() {
        val metrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 250.0, 500.0, 750.0, 1000.0, secondsStep = 90),
            durationSeconds = 360,
            discipline = UserDiscipline.RUNNING
        )

        assertTrue(metrics.distanceKm in 0.99..1.01)
        assertTrue(metrics.averagePaceMinPerKm in 5.9..6.1)
        assertTrue(metrics.averageSpeedKmh in 9.9..10.1)
        assertEquals(5, metrics.validPoints)
    }

    @Test
    fun walkingPaceDoesNotBecomeAbsurdWhenMovementIsSlow() {
        val metrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 250.0, 500.0, 750.0, 1000.0, secondsStep = 300),
            durationSeconds = 1_200,
            discipline = UserDiscipline.RUNNING
        )

        assertTrue(metrics.distanceKm in 0.99..1.01)
        assertTrue(metrics.averagePaceMinPerKm in 19.9..20.1)
        assertTrue(metrics.averageSpeedKmh in 2.9..3.1)
    }

    @Test
    fun runningRejectsJumpAboveTwentyFiveKmh() {
        val metrics = ActivityTrackingRules.summarize(
            points = listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                gpsPointAtMeters(500.0, timestamp = 31_000L),
                gpsPointAtMeters(520.0, timestamp = 91_000L)
            ),
            durationSeconds = 90,
            discipline = UserDiscipline.RUNNING
        )

        assertEquals(0.0, metrics.distanceKm, 0.001)
        assertEquals(0.0, metrics.averagePaceMinPerKm, 0.001)
        assertTrue(metrics.discardedSegments >= 1)
        assertTrue(metrics.isGpsUnstable)
    }

    @Test
    fun cyclingAllowsFasterSegmentsButRejectsAboveSeventyKmh() {
        val plausibleRide = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 2_500.0, 5_000.0, 7_500.0, 10_000.0, secondsStep = 450),
            durationSeconds = 1_800,
            discipline = UserDiscipline.CYCLING
        )
        val impossibleRide = ActivityTrackingRules.summarize(
            points = listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                gpsPointAtMeters(2_000.0, timestamp = 61_000L),
                gpsPointAtMeters(2_050.0, timestamp = 121_000L)
            ),
            durationSeconds = 120,
            discipline = UserDiscipline.CYCLING
        )

        assertTrue(plausibleRide.distanceKm in 9.95..10.05)
        assertTrue(plausibleRide.averageSpeedKmh in 19.9..20.1)
        assertEquals(0.0, impossibleRide.distanceKm, 0.001)
        assertTrue(impossibleRide.discardedSegments >= 1)
    }

    @Test
    fun rejectsInvalidCoordinatesAndBadAccuracy() {
        val metrics = ActivityTrackingRules.summarize(
            points = listOf(
                GpsRoutePoint(91.0, -99.13320, accuracy = 8f, timestamp = 1_000L),
                gpsPointAtMeters(0.0, accuracy = 0f, timestamp = 2_000L),
                gpsPointAtMeters(100.0, accuracy = 35f, timestamp = 62_000L),
                gpsPointAtMeters(200.0, accuracy = 8f, timestamp = 122_000L)
            ),
            durationSeconds = 122,
            discipline = UserDiscipline.RUNNING
        )

        assertEquals(0.0, metrics.distanceKm, 0.001)
        assertEquals(3, metrics.discardedPoints)
    }

    @Test
    fun cyclingAcceptsFiftyMeterAccuracyButRunningDoesNot() {
        val points = routeEveryMeters(
            0.0,
            200.0,
            400.0,
            600.0,
            accuracy = 45f,
            secondsStep = 90
        )

        val running = ActivityTrackingRules.summarize(points, durationSeconds = 270, discipline = UserDiscipline.RUNNING)
        val cycling = ActivityTrackingRules.summarize(points, durationSeconds = 270, discipline = UserDiscipline.CYCLING)

        assertEquals(0.0, running.distanceKm, 0.001)
        assertTrue(cycling.distanceKm in 0.59..0.61)
    }

    @Test
    fun ignoresTinyJitterSegmentsWhenSummarizingDistance() {
        val metrics = ActivityTrackingRules.summarize(
            points = listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                gpsPointAtMeters(1.0, timestamp = 3_000L),
                gpsPointAtMeters(2.0, timestamp = 5_000L),
                gpsPointAtMeters(3.0, timestamp = 7_000L)
            ),
            durationSeconds = 30,
            discipline = UserDiscipline.RUNNING
        )

        assertEquals(0.0, metrics.distanceKm, 0.001)
        assertEquals(0.0, metrics.averagePaceMinPerKm, 0.001)
        assertTrue(metrics.discardedSegments >= 1)
    }

    @Test
    fun doesNotCalculateDistanceUntilThreeValidPoints() {
        val metrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 100.0, secondsStep = 60),
            durationSeconds = 60,
            discipline = UserDiscipline.RUNNING
        )

        assertEquals(0.0, metrics.distanceKm, 0.001)
        assertEquals(0.0, metrics.averagePaceMinPerKm, 0.001)
        assertEquals(0.0, metrics.averageSpeedKmh, 0.001)
    }

    @Test
    fun doesNotExposePaceUntilRouteIsReliable() {
        val shortDistanceMetrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 15.0, 30.0, 40.0, secondsStep = 10),
            durationSeconds = 30,
            discipline = UserDiscipline.RUNNING
        )
        val shortDurationMetrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 25.0, 50.0, 75.0, secondsStep = 4),
            durationSeconds = 12,
            discipline = UserDiscipline.RUNNING
        )

        assertTrue(shortDistanceMetrics.distanceKm < 0.05)
        assertEquals(0.0, shortDistanceMetrics.averagePaceMinPerKm, 0.001)
        assertTrue(shortDurationMetrics.distanceKm >= 0.05)
        assertTrue(shortDurationMetrics.durationSeconds < 15)
        assertEquals(0.0, shortDurationMetrics.averagePaceMinPerKm, 0.001)
    }

    @Test
    fun walkingAcceptsSlowPaceButRejectsStaticDrift() {
        val slowWalkMetrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, secondsStep = 15),
            durationSeconds = 75,
            discipline = UserDiscipline.WALKING
        )
        assertTrue(slowWalkMetrics.distanceKm >= 0.09)
        assertTrue(slowWalkMetrics.averagePaceMinPerKm > 0.0)

        val staticDriftMetrics = ActivityTrackingRules.summarize(
            points = routeEveryMeters(0.0, 2.0, 5.0, 1.0, 4.0, secondsStep = 1),
            durationSeconds = 4,
            discipline = UserDiscipline.WALKING
        )
        assertEquals(0.0, staticDriftMetrics.distanceKm, 0.001)
    }

    @Test
    fun newSegmentAfterPauseDoesNotJoinOldAndNewRoute() {
        val metrics = ActivityTrackingRules.summarize(
            points = listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                gpsPointAtMeters(120.0, timestamp = 61_000L),
                gpsPointAtMeters(2_000.0, timestamp = 3_601_000L, startsNewSegment = true),
                gpsPointAtMeters(2_120.0, timestamp = 3_661_000L)
            ),
            durationSeconds = 120,
            discipline = UserDiscipline.RUNNING
        )

        assertTrue(metrics.distanceKm in 0.23..0.25)
        assertEquals(4, metrics.validPoints)
    }

    @Test
    fun routeJsonAndSaveValidationAreStable() {
        val json = ActivityTrackingRules.toRouteJson(
            listOf(gpsPointAtMeters(0.0, speedMps = 2.5f, timestamp = 1L, startsNewSegment = true))
        )

        assertTrue(json.contains("\"lat\":19.4326000"))
        assertTrue(json.contains("\"seg\":true"))
        assertTrue(ActivityTrackingRules.fromRouteJson(json).first().startsNewSegment)
        assertFalse(ActivityTrackingRules.canSave(distanceKm = 0.0, durationSeconds = 3))
        assertTrue(ActivityTrackingRules.canSave(distanceKm = 0.05, durationSeconds = 30))
    }

    @Test
    fun routeGeoJsonUsesLongitudeLatitudeOrderForMapLibre() {
        val geoJson = ActivityTrackingRules.toRouteGeoJson(
            listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                GpsRoutePoint(19.43360, -99.13420, accuracy = 8f, timestamp = 61_000L),
                GpsRoutePoint(19.43460, -99.13520, accuracy = 8f, timestamp = 121_000L)
            ),
            discipline = UserDiscipline.RUNNING
        )

        assertEquals(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-99.1332000,19.4326000],[-99.1342000,19.4336000],[-99.1352000,19.4346000]]},\"properties\":{}}",
            geoJson
        )
    }

    @Test
    fun routeGeoJsonCutsSegmentsAfterPause() {
        val geoJson = ActivityTrackingRules.toRouteGeoJson(
            listOf(
                gpsPointAtMeters(0.0, timestamp = 1_000L),
                gpsPointAtMeters(120.0, timestamp = 61_000L),
                gpsPointAtMeters(2_000.0, timestamp = 3_601_000L, startsNewSegment = true),
                gpsPointAtMeters(2_120.0, timestamp = 3_661_000L)
            ),
            discipline = UserDiscipline.RUNNING
        )

        assertTrue(geoJson.contains("\"MultiLineString\""))
        assertFalse(geoJson.contains("[[-99.1332000,19.4336792],[-99.1332000,19.4505864]]"))
    }

    @Test
    fun routeGeoJsonDoesNotCrashWithEmptyRoute() {
        assertEquals(
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[]},\"properties\":{}}",
            ActivityTrackingRules.toRouteGeoJson(emptyList())
        )
    }

    private fun routeEveryMeters(
        vararg meters: Double,
        accuracy: Float = 8f,
        secondsStep: Long
    ): List<GpsRoutePoint> {
        return meters.mapIndexed { index, meter ->
            gpsPointAtMeters(
                metersNorth = meter,
                accuracy = accuracy,
                timestamp = 1_000L + index * secondsStep * 1_000L
            )
        }
    }

    private fun gpsPointAtMeters(
        metersNorth: Double,
        accuracy: Float = 8f,
        speedMps: Float = 0f,
        timestamp: Long,
        startsNewSegment: Boolean = false
    ): GpsRoutePoint {
        return GpsRoutePoint(
            latitude = 19.4326000 + metersNorth / 111_195.0,
            longitude = -99.1332000,
            accuracy = accuracy,
            speedMps = speedMps,
            timestamp = timestamp,
            startsNewSegment = startsNewSegment
        )
    }
}
