package com.example.ui.muscles

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.BodyModel
import com.example.data.model.Muscle
import com.example.data.model.MuscleIntensity
import com.example.ui.theme.FitPlanTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyHeatMapScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val mockHeatmap = mapOf(
        Muscle.CHEST to MuscleIntensity(Muscle.CHEST, 80, null, 5, 2500f),
        Muscle.BICEPS to MuscleIntensity(Muscle.BICEPS, 40, null, 2, 800f),
        Muscle.QUADS to MuscleIntensity(Muscle.QUADS, 60, null, 4, 1800f)
    )

    @Test
    @Config(qualifiers = "w320dp-h480dp-xhdpi", sdk = [35])
    fun bodyHeatmap_smallScreen() {
        composeTestRule.setContent {
            FitPlanTheme {
                BodyHeatMapCanvas(
                    bodyModel = BodyModel.MALE,
                    heatmap = mockHeatmap,
                    selected = null,
                    onSelect = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/body_heatmap_small.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
    fun bodyHeatmap_normalScreen() {
        composeTestRule.setContent {
            FitPlanTheme {
                BodyHeatMapCanvas(
                    bodyModel = BodyModel.MALE,
                    heatmap = mockHeatmap,
                    selected = Muscle.CHEST,
                    onSelect = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/body_heatmap_normal.png")
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi", sdk = [35])
    fun bodyHeatmap_tabletScreen() {
        composeTestRule.setContent {
            FitPlanTheme {
                BodyHeatMapCanvas(
                    bodyModel = BodyModel.FEMALE,
                    heatmap = mockHeatmap,
                    selected = null,
                    onSelect = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/body_heatmap_tablet.png")
    }
}
