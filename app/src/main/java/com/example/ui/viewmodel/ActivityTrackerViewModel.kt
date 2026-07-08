package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityTrackingRules
import com.example.data.model.GpsRoutePoint
import com.example.data.model.UserDiscipline
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TrackerStatus {
    READY,
    NEEDS_PERMISSION,
    SEARCHING_GPS,
    TRACKING,
    PAUSED,
    FINISHED,
    ERROR
}

data class ActivityTrackerState(
    val discipline: UserDiscipline = UserDiscipline.RUNNING,
    val status: TrackerStatus = TrackerStatus.READY,
    val points: List<GpsRoutePoint> = emptyList(),
    val currentLocation: GpsRoutePoint? = null,
    val hasPreciseLocation: Boolean = false,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceKm: Double = 0.0,
    val averagePaceMinPerKm: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val validGpsPoints: Int = 0,
    val discardedGpsPoints: Int = 0,
    val discardedGpsSegments: Int = 0,
    val lastGpsAccuracyMeters: Float = 0f,
    val gpsSignalLabel: String = "Sin senal",
    val isGpsUnstable: Boolean = false,
    val message: String = "Listo para iniciar"
)

class ActivityTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val _state = MutableStateFlow(ActivityTrackerState())
    val state: StateFlow<ActivityTrackerState> = _state.asStateFlow()
    private var timerJob: Job? = null
    private var startNewSegmentOnNextPoint: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val current = _state.value
            val isTracking = current.status == TrackerStatus.TRACKING || current.status == TrackerStatus.SEARCHING_GPS
            val isReady = current.status == TrackerStatus.READY
            if (!isTracking && !isReady) return
            val location = result.lastLocation ?: return
            val point = GpsRoutePoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            acceptLocationPoint(point, addToRoute = isTracking)
        }
    }

    @SuppressLint("MissingPermission")
    fun prepare(discipline: UserDiscipline) {
        if (_state.value.discipline != discipline) {
            reset(discipline)
        }
        when {
            !hasLocationPermission() -> {
                _state.value = _state.value.copy(
                    status = TrackerStatus.NEEDS_PERMISSION,
                    hasPreciseLocation = false,
                    message = "Permite ubicacion para registrar tu recorrido."
                )
            }
            !isDeviceLocationEnabled() -> {
                _state.value = _state.value.copy(
                    status = TrackerStatus.ERROR,
                    hasPreciseLocation = hasFineLocationPermission(),
                    message = "Activa la ubicacion del dispositivo para iniciar."
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    status = if (_state.value.status == TrackerStatus.NEEDS_PERMISSION || _state.value.status == TrackerStatus.ERROR) {
                        TrackerStatus.READY
                    } else {
                        _state.value.status
                    },
                    hasPreciseLocation = hasFineLocationPermission(),
                    message = preciseLocationMessage("Buscando ubicación...")
                )
                requestInitialLocation(addToRoute = false)
                requestLocationUpdates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(discipline: UserDiscipline) {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(status = TrackerStatus.NEEDS_PERMISSION, message = "Permite ubicacion para registrar tu recorrido.")
            return
        }
        if (!isDeviceLocationEnabled()) {
            _state.value = _state.value.copy(status = TrackerStatus.ERROR, message = "Activa la ubicacion del dispositivo para iniciar.")
            return
        }
        val now = System.currentTimeMillis()
        _state.value = ActivityTrackerState(
            discipline = discipline,
            status = TrackerStatus.SEARCHING_GPS,
            currentLocation = _state.value.currentLocation,
            hasPreciseLocation = hasFineLocationPermission(),
            startedAt = now,
            message = preciseLocationMessage("Buscando senal GPS...")
        )
        startNewSegmentOnNextPoint = false
        startTimer()
        requestInitialLocation(addToRoute = true)
        requestLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    fun resume() {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(status = TrackerStatus.NEEDS_PERMISSION, message = "Permite ubicacion para registrar tu recorrido.")
            return
        }
        if (!isDeviceLocationEnabled()) {
            _state.value = _state.value.copy(status = TrackerStatus.ERROR, message = "Activa la ubicacion del dispositivo para iniciar.")
            return
        }
        startNewSegmentOnNextPoint = true
        _state.value = _state.value.copy(status = TrackerStatus.SEARCHING_GPS, message = preciseLocationMessage("Reanudando GPS"))
        requestInitialLocation(addToRoute = false)
        requestLocationUpdates()
    }

    fun pause() {
        locationClient.removeLocationUpdates(locationCallback)
        startNewSegmentOnNextPoint = true
        _state.value = _state.value.copy(status = TrackerStatus.PAUSED, message = "Sesion pausada")
    }

    fun finish() {
        locationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        val current = _state.value
        val endedAt = System.currentTimeMillis()
        val metrics = ActivityTrackingRules.summarize(current.points, current.elapsedSeconds, current.discipline)
        _state.value = current.copy(
            status = TrackerStatus.FINISHED,
            endedAt = endedAt,
            distanceKm = metrics.distanceKm,
            averagePaceMinPerKm = metrics.averagePaceMinPerKm,
            averageSpeedKmh = metrics.averageSpeedKmh,
            currentSpeedKmh = 0.0,
            validGpsPoints = metrics.validPoints,
            discardedGpsPoints = metrics.discardedPoints,
            discardedGpsSegments = metrics.discardedSegments,
            lastGpsAccuracyMeters = metrics.lastAccuracyMeters,
            gpsSignalLabel = gpsSignalLabel(metrics.signalQuality),
            isGpsUnstable = metrics.isGpsUnstable,
            message = if (ActivityTrackingRules.canSave(metrics.distanceKm, metrics.durationSeconds)) {
                "Sesion lista para guardar"
            } else {
                "Necesitas mas distancia o tiempo para guardar"
            }
        )
    }

    fun reset(discipline: UserDiscipline = _state.value.discipline) {
        locationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        startNewSegmentOnNextPoint = false
        _state.value = ActivityTrackerState(
            discipline = discipline,
            currentLocation = _state.value.currentLocation,
            hasPreciseLocation = hasFineLocationPermission(),
            message = preciseLocationMessage("Listo para iniciar")
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(3f)
                .build()
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                status = TrackerStatus.ERROR,
                message = "Permiso de ubicación revocado: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestInitialLocation(addToRoute: Boolean) {
        try {
            locationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null && isFreshLocation(location)) {
                        acceptLocationPoint(location.toGpsRoutePoint(), addToRoute = addToRoute)
                    } else {
                        requestCurrentLocation(addToRoute)
                    }
                }
                .addOnFailureListener {
                    requestCurrentLocation(addToRoute)
                }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                status = TrackerStatus.ERROR,
                message = "Permiso de ubicación revocado: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(addToRoute: Boolean) {
        try {
            val cancellationToken = CancellationTokenSource()
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        acceptLocationPoint(location.toGpsRoutePoint(), addToRoute = addToRoute)
                    } else {
                        _state.value = _state.value.copy(status = TrackerStatus.SEARCHING_GPS, message = "Buscando senal GPS...")
                    }
                }
                .addOnFailureListener {
                    _state.value = _state.value.copy(status = TrackerStatus.ERROR, message = "Activa la ubicacion del dispositivo para iniciar.")
                }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(
                status = TrackerStatus.ERROR,
                message = "Permiso de ubicación revocado: ${e.message}"
            )
        }
    }

    private fun acceptLocationPoint(point: GpsRoutePoint, addToRoute: Boolean) {
        val current = _state.value
        if (!isFreshGpsPoint(point) || !ActivityTrackingRules.isUsablePoint(point, current.discipline)) {
            _state.value = current.copy(
                status = if (current.status == TrackerStatus.TRACKING) TrackerStatus.SEARCHING_GPS else current.status,
                currentLocation = point,
                discardedGpsPoints = current.discardedGpsPoints + 1,
                lastGpsAccuracyMeters = point.accuracy.coerceAtLeast(0f),
                gpsSignalLabel = gpsSignalLabel(ActivityTrackingRules.signalQualityForAccuracy(point.accuracy)),
                isGpsUnstable = true,
                message = "Senal GPS debil, sal a un lugar abierto"
            )
            return
        }
        if (!addToRoute) {
            val signalQuality = ActivityTrackingRules.signalQualityForAccuracy(point.accuracy)
            val readyStatus = current.status == TrackerStatus.READY ||
                current.status == TrackerStatus.NEEDS_PERMISSION ||
                current.status == TrackerStatus.ERROR
            _state.value = if (readyStatus) {
                current.copy(
                    currentLocation = point,
                    hasPreciseLocation = hasFineLocationPermission(),
                    lastGpsAccuracyMeters = point.accuracy,
                    gpsSignalLabel = gpsSignalLabel(signalQuality),
                    message = preciseLocationMessage("GPS listo")
                )
            } else {
                current.copy(
                    currentLocation = point,
                    hasPreciseLocation = hasFineLocationPermission(),
                    lastGpsAccuracyMeters = point.accuracy,
                    gpsSignalLabel = gpsSignalLabel(signalQuality)
                )
            }
            return
        }
        if (current.status == TrackerStatus.READY) {
            _state.value = current.copy(
                currentLocation = point,
                hasPreciseLocation = hasFineLocationPermission()
            )
            return
        }
        val routePoint = if (startNewSegmentOnNextPoint && current.points.isNotEmpty()) {
            point.copy(startsNewSegment = true)
        } else {
            point
        }
        val previous = current.points.lastOrNull()
        if (previous != null && !routePoint.startsNewSegment && !ActivityTrackingRules.isPlausibleNextPoint(previous, routePoint, current.discipline)) {
            _state.value = current.copy(
                currentLocation = point,
                status = TrackerStatus.SEARCHING_GPS,
                discardedGpsSegments = current.discardedGpsSegments + 1,
                lastGpsAccuracyMeters = point.accuracy,
                gpsSignalLabel = gpsSignalLabel(ActivityTrackingRules.signalQualityForAccuracy(point.accuracy)),
                isGpsUnstable = true,
                message = "Senal GPS debil, sal a un lugar abierto"
            )
            return
        }
        startNewSegmentOnNextPoint = false
        val nextPoints = current.points + routePoint
        val metrics = ActivityTrackingRules.summarize(nextPoints, current.elapsedSeconds, current.discipline)
        _state.value = current.copy(
            status = TrackerStatus.TRACKING,
            points = nextPoints,
            currentLocation = point,
            hasPreciseLocation = hasFineLocationPermission(),
            distanceKm = metrics.distanceKm,
            averagePaceMinPerKm = metrics.averagePaceMinPerKm,
            averageSpeedKmh = metrics.averageSpeedKmh,
            currentSpeedKmh = metrics.currentSpeedKmh,
            validGpsPoints = metrics.validPoints,
            discardedGpsPoints = current.discardedGpsPoints + metrics.discardedPoints,
            discardedGpsSegments = metrics.discardedSegments + current.discardedGpsSegments,
            lastGpsAccuracyMeters = metrics.lastAccuracyMeters,
            gpsSignalLabel = gpsSignalLabel(metrics.signalQuality),
            isGpsUnstable = metrics.isGpsUnstable || current.discardedGpsSegments > 0,
            message = trackingMessage(metrics, nextPoints.size)
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _state.value
                if (current.status == TrackerStatus.TRACKING || current.status == TrackerStatus.SEARCHING_GPS) {
                    val elapsed = current.elapsedSeconds + 1
                    val metrics = ActivityTrackingRules.summarize(current.points, elapsed, current.discipline)
                    _state.value = current.copy(
                        elapsedSeconds = elapsed,
                        distanceKm = metrics.distanceKm,
                        averagePaceMinPerKm = metrics.averagePaceMinPerKm,
                        averageSpeedKmh = metrics.averageSpeedKmh,
                        currentSpeedKmh = metrics.currentSpeedKmh,
                        validGpsPoints = metrics.validPoints,
                        discardedGpsPoints = current.discardedGpsPoints,
                        lastGpsAccuracyMeters = metrics.lastAccuracyMeters,
                        gpsSignalLabel = gpsSignalLabel(metrics.signalQuality),
                        isGpsUnstable = metrics.isGpsUnstable || current.discardedGpsSegments > 0
                    )
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDeviceLocationEnabled(): Boolean {
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun isFreshLocation(location: Location): Boolean {
        val locationTime = location.time
        return locationTime > 0L && System.currentTimeMillis() - locationTime <= 120_000L
    }

    private fun isFreshGpsPoint(point: GpsRoutePoint): Boolean {
        return point.timestamp > 0L && System.currentTimeMillis() - point.timestamp <= 120_000L
    }

    private fun Location.toGpsRoutePoint(): GpsRoutePoint {
        return GpsRoutePoint(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = if (hasAccuracy()) accuracy else 0f,
            speedMps = if (hasSpeed()) speed else 0f,
            timestamp = time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun preciseLocationMessage(base: String): String {
        return if (hasFineLocationPermission()) {
            base
        } else {
            "Activa ubicacion precisa para medir mejor tu recorrido."
        }
    }

    private fun gpsSignalLabel(signalQuality: com.example.data.model.GpsSignalQuality): String {
        return when (signalQuality) {
            com.example.data.model.GpsSignalQuality.GOOD -> "Buena"
            com.example.data.model.GpsSignalQuality.REGULAR -> "Regular"
            com.example.data.model.GpsSignalQuality.WEAK -> "Debil"
            com.example.data.model.GpsSignalQuality.NONE -> "Sin senal"
        }
    }

    private fun trackingMessage(metrics: com.example.data.model.GpsSessionMetrics, routePointCount: Int): String {
        return when {
            metrics.isGpsUnstable -> "Senal GPS inestable; descartando saltos."
            routePointCount <= 1 -> "Comienza a moverte para calcular distancia"
            metrics.validPoints < 3 -> "Reuniendo puntos GPS confiables"
            else -> "Grabando tu recorrido"
        }
    }

    override fun onCleared() {
        locationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        super.onCleared()
    }
}
