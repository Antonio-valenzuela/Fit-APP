package com.example.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZonedDateTime

class HealthConnectManager(
    private val context: Context,
    private val availabilityProvider: () -> Boolean = {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    },
    private val clientProvider: () -> HealthConnectClient? = {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            null
        }
    }
) {

    private val healthConnectClient by lazy {
        if (isHealthConnectAvailable()) {
            clientProvider()
        } else {
            null
        }
    }

    fun isHealthConnectAvailable(): Boolean = availabilityProvider()

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(requiredPermissions)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): List<StepsRecord> {
        val client = healthConnectClient ?: return emptyList()
        if (!hasAllPermissions()) return emptyList()

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun writeSteps(steps: Long, startTime: Instant, endTime: Instant) {
        val client = healthConnectClient ?: return
        if (!hasAllPermissions()) return

        try {
            // Idempotency check: verify if identical steps record exists in timeframe
            val existing = readSteps(startTime, endTime)
            val alreadyExists = existing.any { it.count == steps }
            if (alreadyExists) return

            val record = StepsRecord(
                count = steps,
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = ZonedDateTime.now().offset,
                endZoneOffset = ZonedDateTime.now().offset
            )
            client.insertRecords(listOf(record))
        } catch (e: Exception) {
            // Fallback gracefully
        }
    }
}
