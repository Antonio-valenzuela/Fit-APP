package com.example

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.api.GeminiImageRequest
import com.example.ui.nutrition.GeminiFoodPhase
import com.example.ui.viewmodel.FitPlanViewModel
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiUiLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var viewModel: FitPlanViewModel

    @Before
    fun launchNutritionDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitplan_mx_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_logged_in", true)
            .putBoolean("onboarding_completed", true)
            .putBoolean("feature_nutrition_enabled", true)
            .putBoolean("feature_animations_enabled", false)
            .commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 224").close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel = ViewModelProvider(it)[FitPlanViewModel::class.java]
        }
        waitForTag("dashboard_screen")
        composeRule.onNodeWithTag("bottom_nav_nutrition").performClick()
        waitForTag("nutrition_screen")
        composeRule.onNodeWithTag("primary_register_food_button").performClick()
        waitForTag("food_entry_dialog")
    }

    @After
    fun closeActivity() {
        if (::viewModel.isInitialized) viewModel.cancelGeminiFoodAnalysis()
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun consentCanBeCancelledWithoutSendingAndRequestRemainsRetryable() {
        preparePublicApple()
        waitForTag("gemini_consent_dialog")
        composeRule.onNodeWithTag("gemini_consent_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("gemini_consent_checkbox").performClick()
        composeRule.onNodeWithTag("gemini_consent_send").assertIsEnabled()
        composeRule.onNodeWithTag("gemini_consent_cancel").performClick()

        waitForTag("gemini_phase_cancelled")
        composeRule.onNodeWithTag("gemini_retry_button").assertIsEnabled()
        composeRule.onNodeWithTag("gemini_cancel_or_manual_button").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            viewModel.geminiFoodUiState.value.phase == GeminiFoodPhase.IDLE
        }
        composeRule.onNodeWithText("Registro manual activo. Puedes escribir los datos.").assertIsDisplayed()
    }

    @Test
    fun preparedConsentSurvivesRecreationAndBackgroundReturnWithoutSending() {
        preparePublicApple()
        waitForTag("gemini_consent_dialog")
        assertEquals(GeminiFoodPhase.AWAITING_CONSENT, viewModel.geminiFoodUiState.value.phase)

        scenario.recreate()
        scenario.onActivity { viewModel = ViewModelProvider(it)[FitPlanViewModel::class.java] }
        waitForTag("gemini_consent_dialog")
        assertEquals(GeminiFoodPhase.AWAITING_CONSENT, viewModel.geminiFoodUiState.value.phase)

        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("gemini_consent_dialog")
        assertEquals(GeminiFoodPhase.AWAITING_CONSENT, viewModel.geminiFoodUiState.value.phase)
        composeRule.onNodeWithTag("gemini_consent_cancel").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            viewModel.geminiFoodUiState.value.phase == GeminiFoodPhase.CANCELLED
        }
    }

    @Test
    fun repeatedConsentCancellationNeverStartsARequest() {
        repeat(5) {
            preparePublicApple()
            waitForTag("gemini_consent_dialog")
            assertEquals(GeminiFoodPhase.AWAITING_CONSENT, viewModel.geminiFoodUiState.value.phase)
            composeRule.onNodeWithTag("gemini_consent_cancel").performClick()
            composeRule.waitUntil(timeoutMillis = 20_000) {
                viewModel.geminiFoodUiState.value.phase == GeminiFoodPhase.CANCELLED
            }
            composeRule.onNodeWithTag("gemini_cancel_or_manual_button").performClick()
            composeRule.waitUntil(timeoutMillis = 20_000) {
                viewModel.geminiFoodUiState.value.phase == GeminiFoodPhase.IDLE
            }
        }
    }

    @Ignore("La solicitud real ya se ejecutó una vez; staging es incompatible y no debe repetirse")
    @Test
    fun onePublicImageTraversesLoadingAndARecoverableFinalState() {
        preparePublicApple()
        waitForTag("gemini_consent_dialog")
        composeRule.onNodeWithTag("gemini_consent_checkbox").performClick()
        composeRule.onNodeWithTag("gemini_consent_send").performClick()
        waitForTag("gemini_phase_loading")

        composeRule.waitUntil(timeoutMillis = 60_000) {
            viewModel.geminiFoodUiState.value.phase in setOf(GeminiFoodPhase.REVIEW, GeminiFoodPhase.ERROR)
        }
        when (viewModel.geminiFoodUiState.value.phase) {
            GeminiFoodPhase.REVIEW -> {
                waitForTag("gemini_phase_review")
                scrollToTag("save_meal_button")
                composeRule.onNodeWithTag("save_meal_button").performClick()
                composeRule.waitUntil(timeoutMillis = 20_000) {
                    composeRule.onAllNodesWithTag("food_entry_dialog").fetchSemanticsNodes().isEmpty()
                }
            }
            GeminiFoodPhase.ERROR -> {
                waitForTag("gemini_phase_error")
                composeRule.onNodeWithTag("gemini_retry_button").assertIsEnabled()
                composeRule.onNodeWithTag("gemini_cancel_or_manual_button").assertIsEnabled()
            }
            else -> error("Fase final inesperada: ${viewModel.geminiFoodUiState.value.phase}")
        }
    }

    private fun preparePublicApple() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("cat /data/local/tmp/fitplan-gemini-ui/simple_apple_balanced.jpg")
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        assertEquals(107_844, bytes.size)
        assertEquals(
            "ff1812f1fe35312c58c0d08d2c9d5c81fd1b12514ff8c090302afcb147f97fd1",
            bytes.sha256()
        )
        viewModel.prepareGeminiFood(
            GeminiImageRequest(
                imageBytes = bytes,
                mimeType = "image/jpeg",
                originalBytes = 581_835,
                originalWidth = 1_920,
                originalHeight = 2_560,
                preparedWidth = 960,
                preparedHeight = 1_280,
                quality = 75
            ),
            description = "manzana roja"
        )
    }

    private fun scrollToTag(tag: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
}
