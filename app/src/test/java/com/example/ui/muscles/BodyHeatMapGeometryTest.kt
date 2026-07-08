package com.example.ui.muscles

import com.example.data.model.BodyModel
import com.example.data.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BodyHeatMapGeometryTest {

    // Helper to run hit tests using our new config classes directly to test pure logic
    private fun testHit(model: BodyModel, view: BodyView, xNorm: Float, yNorm: Float): Muscle? {
        val config = MuscleHitboxRegistry.getConfig(model, view)
        val point = ConfigPoint(xNorm, yNorm)
        if (!config.crop.contains(point)) return null

        val bodyPoint = ConfigPoint(
            x = (xNorm - config.crop.left) / config.crop.width,
            y = (yNorm - config.crop.top) / config.crop.height
        )

        return config.regions
            .asSequence()
            .filter { region -> region.contains(bodyPoint) }
            .maxByOrNull { it.priority }
            ?.muscle
    }

    @Test
    fun testMaleFrontGeometryPoints() {
        // 5 test points for Male Front
        assertEquals(Muscle.CHEST, testHit(BodyModel.MALE, BodyView.FRONT, 0.402f, 0.248f))
        assertEquals(Muscle.SHOULDERS, testHit(BodyModel.MALE, BodyView.FRONT, 0.245f, 0.208f))
        assertEquals(Muscle.ABS, testHit(BodyModel.MALE, BodyView.FRONT, 0.500f, 0.447f))
        assertEquals(Muscle.BICEPS, testHit(BodyModel.MALE, BodyView.FRONT, 0.186f, 0.328f))
        assertEquals(Muscle.QUADS, testHit(BodyModel.MALE, BodyView.FRONT, 0.362f, 0.686f))
    }

    @Test
    fun testMaleBackGeometryPoints() {
        // 5 test points for Male Back
        assertEquals(Muscle.TRAPS, testHit(BodyModel.MALE, BodyView.BACK, 0.500f, 0.199f))
        assertEquals(Muscle.BACK, testHit(BodyModel.MALE, BodyView.BACK, 0.356f, 0.358f))
        assertEquals(Muscle.TRICEPS, testHit(BodyModel.MALE, BodyView.BACK, 0.192f, 0.358f))
        assertEquals(Muscle.GLUTES, testHit(BodyModel.MALE, BodyView.BACK, 0.404f, 0.567f))
        assertEquals(Muscle.HAMSTRINGS, testHit(BodyModel.MALE, BodyView.BACK, 0.384f, 0.736f))
    }

    @Test
    fun testFemaleFrontGeometryPoints() {
        // 5 test points for Female Front
        assertEquals(Muscle.CHEST, testHit(BodyModel.FEMALE, BodyView.FRONT, 0.404f, 0.288f))
        assertEquals(Muscle.SHOULDERS, testHit(BodyModel.FEMALE, BodyView.FRONT, 0.240f, 0.218f))
        assertEquals(Muscle.ABS, testHit(BodyModel.FEMALE, BodyView.FRONT, 0.500f, 0.477f))
        assertEquals(Muscle.QUADS, testHit(BodyModel.FEMALE, BodyView.FRONT, 0.365f, 0.686f))
        assertEquals(Muscle.BICEPS, testHit(BodyModel.FEMALE, BodyView.FRONT, 0.183f, 0.348f))
    }

    @Test
    fun testFemaleBackGeometryPoints() {
        // 5 test points for Female Back
        assertEquals(Muscle.TRAPS, testHit(BodyModel.FEMALE, BodyView.BACK, 0.500f, 0.199f))
        assertEquals(Muscle.BACK, testHit(BodyModel.FEMALE, BodyView.BACK, 0.353f, 0.358f))
        assertEquals(Muscle.GLUTES, testHit(BodyModel.FEMALE, BodyView.BACK, 0.382f, 0.577f))
        assertEquals(Muscle.TRICEPS, testHit(BodyModel.FEMALE, BodyView.BACK, 0.166f, 0.368f))
        assertEquals(Muscle.HAMSTRINGS, testHit(BodyModel.FEMALE, BodyView.BACK, 0.382f, 0.736f))
    }

    @Test
    fun testEdgeAndBorderHits() {
        // Point clearly outside any muscle region (e.g. background space)
        assertNull(testHit(BodyModel.MALE, BodyView.FRONT, 0.05f, 0.05f))
        assertNull(testHit(BodyModel.MALE, BodyView.FRONT, 0.95f, 0.95f))

        // Point outside the crop entirely
        assertNull(testHit(BodyModel.MALE, BodyView.FRONT, 0.005f, 0.5f))
    }

    @Test
    fun testCanvasScalingAndNormalizedConsistency() {
        val imageAspectRatio = 682f / 1387f

        // Screen 1: 360dp x 600dp (Compact screen)
        val w1 = 360f
        val h1 = 600f

        // Let's assume a tap at a specific absolute pixel coord on Screen 1
        // and project it to normalized image coordinates.
        val bounds1 = HitboxTransformer.calculateImageDrawBounds(w1, h1, imageAspectRatio)
        // Let's place a tap exactly in the center of the drawn image
        val tapX1 = bounds1.left + bounds1.width * 0.5f
        val tapY1 = bounds1.top + bounds1.height * 0.447f

        val norm1 = HitboxTransformer.transformCanvasToNormalized(tapX1, tapY1, w1, h1, imageAspectRatio)
        assertNotNull(norm1)
        assertEquals(0.5f, norm1!!.x, 0.001f)
        assertEquals(0.447f, norm1.y, 0.001f)

        // Screen 2: 800dp x 1200dp (Tablet/Large screen)
        val w2 = 800f
        val h2 = 1200f
        val bounds2 = HitboxTransformer.calculateImageDrawBounds(w2, h2, imageAspectRatio)
        // Projecting the same normalized point (0.5f, 0.447f) to Screen 2 absolute pixels:
        val tapX2 = bounds2.left + bounds2.width * 0.5f
        val tapY2 = bounds2.top + bounds2.height * 0.447f

        val norm2 = HitboxTransformer.transformCanvasToNormalized(tapX2, tapY2, w2, h2, imageAspectRatio)
        assertNotNull(norm2)
        assertEquals(0.5f, norm2!!.x, 0.001f)
        assertEquals(0.447f, norm2.y, 0.001f)

        // Both canvas taps on different screen sizes map to the exact same normalized coordinates and same muscle!
        val muscle1 = testHit(BodyModel.MALE, BodyView.FRONT, norm1.x, norm1.y)
        val muscle2 = testHit(BodyModel.MALE, BodyView.FRONT, norm2.x, norm2.y)
        assertEquals(Muscle.ABS, muscle1)
        assertEquals(muscle1, muscle2)
    }
}
