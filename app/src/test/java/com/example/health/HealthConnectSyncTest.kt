package com.example.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthConnectSyncTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : androidx.health.connect.client.records.Record> createReadRecordsResponse(
        records: List<T>,
        pageToken: String? = null
    ): ReadRecordsResponse<T> {
        val constructor = ReadRecordsResponse::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 2 &&
            it.parameterTypes[0] == List::class.java &&
            it.parameterTypes[1] == String::class.java
        } ?: throw IllegalStateException("Could not find ReadRecordsResponse constructor")

        constructor.isAccessible = true
        return constructor.newInstance(records, pageToken) as ReadRecordsResponse<T>
    }

    private fun createMockClient(
        grantedPermissions: Set<String>,
        stepsRecords: List<StepsRecord>,
        onInsertRecords: ((List<androidx.health.connect.client.records.Record>) -> Unit)? = null
    ): HealthConnectClient {
        val permissionControllerProxy = Proxy.newProxyInstance(
            PermissionController::class.java.classLoader,
            arrayOf(PermissionController::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "getGrantedPermissions") {
                        return grantedPermissions
                    }
                    return null
                }
            }
        ) as PermissionController

        return Proxy.newProxyInstance(
            HealthConnectClient::class.java.classLoader,
            arrayOf(HealthConnectClient::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    when (method?.name) {
                        "getPermissionController" -> return permissionControllerProxy
                        "readRecords" -> {
                            return createReadRecordsResponse(stepsRecords)
                        }
                        "insertRecords" -> {
                            val recordsList = args?.get(0) as? List<androidx.health.connect.client.records.Record>
                            if (recordsList != null) {
                                onInsertRecords?.invoke(recordsList)
                            }
                            return null
                        }
                    }
                    return null
                }
            }
        ) as HealthConnectClient
    }

    @Test
    fun testAvailabilityCheck() {
        var isAvailable = false
        val manager = HealthConnectManager(
            context = context,
            availabilityProvider = { isAvailable },
            clientProvider = { null }
        )

        assertFalse(manager.isHealthConnectAvailable())

        isAvailable = true
        assertTrue(manager.isHealthConnectAvailable())
    }

    @Test
    fun testHasAllPermissions() = runTest {
        val allPermissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class)
        )
        // Set up manager with missing permissions
        var mockClient = createMockClient(
            grantedPermissions = setOf(HealthPermission.getReadPermission(StepsRecord::class)),
            stepsRecords = emptyList()
        )
        var manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClient }
        )
        // Since it doesn't match all required permissions, it should return false
        assertFalse(manager.hasAllPermissions())

        // Set up manager with all permissions
        mockClient = createMockClient(
            grantedPermissions = manager.requiredPermissions,
            stepsRecords = emptyList()
        )
        manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClient }
        )
        assertTrue(manager.hasAllPermissions())
    }

    @Test
    fun testReadSteps() = runTest {
        val now = Instant.now()
        val start = now.minusSeconds(3600)
        val dummyRecord = StepsRecord(
            count = 1200,
            startTime = start,
            endTime = now,
            startZoneOffset = ZonedDateTime.now().offset,
            endZoneOffset = ZonedDateTime.now().offset
        )

        val mockClient = createMockClient(
            grantedPermissions = emptySet(), // No permissions
            stepsRecords = listOf(dummyRecord)
        )
        var manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClient }
        )

        // Read steps with no permissions should return empty list
        var steps = manager.readSteps(start, now)
        assertTrue(steps.isEmpty())

        // Read steps with permissions should return the list
        val mockClientWithPerms = createMockClient(
            grantedPermissions = manager.requiredPermissions,
            stepsRecords = listOf(dummyRecord)
        )
        manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClientWithPerms }
        )
        steps = manager.readSteps(start, now)
        assertEquals(1, steps.size)
        assertEquals(1200L, steps[0].count)
    }

    @Test
    fun testWriteStepsIdempotency() = runTest {
        val now = Instant.now()
        val start = now.minusSeconds(3600)
        val dummyRecord = StepsRecord(
            count = 1500,
            startTime = start,
            endTime = now,
            startZoneOffset = ZonedDateTime.now().offset,
            endZoneOffset = ZonedDateTime.now().offset
        )

        var insertCount = 0
        var insertedSteps: Long = 0
        val mockClient = createMockClient(
            grantedPermissions = emptySet(), // We'll configure manager's permissions
            stepsRecords = listOf(dummyRecord),
            onInsertRecords = { records ->
                insertCount++
                insertedSteps = (records[0] as StepsRecord).count
            }
        )

        // If not having permissions, write steps should do nothing
        var manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClient }
        )
        manager.writeSteps(1500L, start, now)
        assertEquals(0, insertCount)

        // Having permissions, but writing identical steps that already exist (idempotent check)
        val mockClientWithPerms = createMockClient(
            grantedPermissions = manager.requiredPermissions,
            stepsRecords = listOf(dummyRecord),
            onInsertRecords = { records ->
                insertCount++
                insertedSteps = (records[0] as StepsRecord).count
            }
        )
        manager = HealthConnectManager(
            context = context,
            availabilityProvider = { true },
            clientProvider = { mockClientWithPerms }
        )
        manager.writeSteps(1500L, start, now)
        assertEquals(0, insertCount) // Should not insert because steps=1500 is already returned by readSteps

        // Having permissions, and writing different steps that do not exist yet
        manager.writeSteps(2000L, start, now)
        assertEquals(1, insertCount)
        assertEquals(2000L, insertedSteps)
    }
}
