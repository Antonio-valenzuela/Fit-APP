package com.example

import android.Manifest
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.UserDiscipline
import com.example.data.model.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpsPermissionsInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private var originalLocationMode = 3

    @Before
    fun rememberDeviceState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        originalLocationMode = shell("settings get secure location_mode").trim().toIntOrNull() ?: 3
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("onboarding_completed", true)
            .putBoolean("feature_animations_enabled", false)
            .commit()
    }

    @After
    fun restoreDeviceState() {
        if (::scenario.isInitialized) scenario.close()
        shell("settings put secure location_mode $originalLocationMode")
    }

    @Test
    fun a_deniedAndUserFixedPermissionRemainsRecoverable() {
        setPermission(Manifest.permission.ACCESS_FINE_LOCATION, false)
        setPermission(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        shell("pm set-permission-flags $PACKAGE ${Manifest.permission.ACCESS_FINE_LOCATION} user-fixed")
        shell("pm set-permission-flags $PACKAGE ${Manifest.permission.ACCESS_COARSE_LOCATION} user-fixed")

        launchFor(UserDiscipline.RUNNING)
        openTracker("active_run", "active_activity_running_screen")
        composeRule.onNodeWithTag("tracker_permission_button").assertIsDisplayed().performClick()
        denyPermissionDialog()
        waitForTag("tracker_permission_button")
        waitForTag("tracker_status_needs_permission")
        composeRule.onNodeWithTag("tracker_status_needs_permission").assertIsDisplayed()
    }

    @Test
    fun b_locationOffShowsErrorThenRunningPausesResumesAndFinishes() {
        grantLocation()
        shell("settings put secure location_mode 0")
        launchFor(UserDiscipline.RUNNING)
        openTracker("active_run", "active_activity_running_screen")
        waitForTag("tracker_retry_button")
        waitForTag("tracker_status_error")
        composeRule.onNodeWithTag("tracker_status_error").assertIsDisplayed()

        shell("settings put secure location_mode 3")
        composeRule.waitUntil(timeoutMillis = 20_000) { shell("settings get secure location_mode").trim() != "0" }
        composeRule.onNodeWithTag("tracker_retry_button").performClick()
        exerciseTrackerControls(backgroundAndReturn = true, alreadyStarted = true)
    }

    @Test
    fun c_walkingUsesTheSameControlledGpsLifecycle() {
        grantLocation()
        shell("settings put secure location_mode 3")
        launchFor(UserDiscipline.WALKING)
        openTracker("active_walk", "active_activity_walking_screen")
        exerciseTrackerControls(backgroundAndReturn = false)
    }

    @Test
    fun d_cyclingUsesTheSameControlledGpsLifecycle() {
        grantLocation()
        shell("settings put secure location_mode 3")
        launchFor(UserDiscipline.CYCLING)
        openTracker("active_ride", "active_activity_cycling_screen")
        exerciseTrackerControls(backgroundAndReturn = false)
    }

    private fun exerciseTrackerControls(backgroundAndReturn: Boolean, alreadyStarted: Boolean = false) {
        if (!alreadyStarted) {
            waitForTag("tracker_start_button")
            composeRule.onNodeWithTag("tracker_start_button").performClick()
        }
        waitForTag("tracker_pause_button")
        composeRule.onNodeWithTag("tracker_pause_button").performClick()
        waitForTag("tracker_resume_button")
        composeRule.onNodeWithTag("tracker_resume_button").performClick()
        waitForTag("tracker_pause_button")
        if (backgroundAndReturn) {
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForTag("tracker_pause_button")
        }
        composeRule.onNodeWithTag("tracker_finish_button").performClick()
        waitForTag("tracker_save_button")
        composeRule.onNodeWithTag("tracker_save_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("tracker_discard_button").performClick()
        waitForTag("tracker_start_button")
    }

    private fun launchFor(discipline: UserDiscipline) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownerId = provisionLocalTestSession(context, "qa.gps@example.com")
        runBlocking {
            val dao = AppDatabase.getInstance(context).userDao()
            val current = dao.getUser(ownerId)
            dao.insertUser(
                (current ?: UserEntity(uid = ownerId, name = "QA Huawei", email = "qa.huawei@example.com"))
                    .copy(trainingDiscipline = discipline.name, disclaimerAccepted = true)
            )
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        waitForTag("dashboard_screen")
    }

    private fun openTracker(bottomRoute: String, screenTag: String) {
        composeRule.onNodeWithTag("bottom_nav_$bottomRoute").performClick()
        waitForTag(screenTag)
    }

    private fun grantLocation() {
        setPermission(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        setPermission(Manifest.permission.ACCESS_FINE_LOCATION, true)
    }

    private fun setPermission(permission: String, granted: Boolean) {
        shell("pm ${if (granted) "grant" else "revoke"} $PACKAGE $permission")
    }

    private fun denyPermissionDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val root = automation.rootInActiveWindow ?: error("No apareció el diálogo de permisos")
        val resourceIds = listOf(
            "com.android.packageinstaller:id/permission_deny_button",
            "com.android.permissioncontroller:id/permission_deny_button"
        )
        val denyNode = resourceIds.asSequence()
            .flatMap { root.findAccessibilityNodeInfosByViewId(it).asSequence() }
            .firstOrNull()
            ?: listOf("NO PERMITIR", "DENEGAR", "DENY")
                .asSequence()
                .flatMap { root.findAccessibilityNodeInfosByText(it).asSequence() }
                .firstOrNull()
            ?: error("El diálogo no expuso un botón de denegación identificable")
        check(denyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "El botón de denegación no aceptó ACTION_CLICK"
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val PACKAGE = "com.aistudio.fitplanmx.app"
    }
}
