package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumAccessPolicyTest {
    @Test
    fun `central testing flag unlocks premium access for testing`() {
        assertTrue(PremiumAccessPolicy.FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING)
        assertTrue(
            PremiumAccessPolicy.isPremiumActive(
                storedPremiumUnlocked = false,
                isDebugBuild = false
            )
        )
    }

    @Test
    fun `premium architecture still works when testing unlock is disabled`() {
        assertFalse(
            PremiumAccessPolicy.isPremiumActive(
                storedPremiumUnlocked = false,
                isDebugBuild = false,
                forceUnlockForTesting = false
            )
        )
        assertTrue(
            PremiumAccessPolicy.isPremiumActive(
                storedPremiumUnlocked = true,
                isDebugBuild = false,
                forceUnlockForTesting = false
            )
        )
    }

    @Test
    fun `release build cannot persist local premium patch`() {
        assertFalse(
            PremiumAccessPolicy.storedPremiumFromLocalPatch(
                enabled = true,
                isDebugBuild = false
            )
        )
    }

    @Test
    fun `debug build can persist local premium patch preference`() {
        assertTrue(
            PremiumAccessPolicy.storedPremiumFromLocalPatch(
                enabled = true,
                isDebugBuild = true
            )
        )
        assertFalse(
            PremiumAccessPolicy.storedPremiumFromLocalPatch(
                enabled = false,
                isDebugBuild = true
            )
        )
    }

    @Test
    fun `central testing flag enables Gemini food scan even when feature flag is off`() {
        assertTrue(
            PremiumAccessPolicy.canUseGeminiFoodScan(
                isPremiumActive = false,
                featureEnabled = false,
                isDebugBuild = false
            )
        )
    }

    @Test
    fun `release build requires premium and enabled Gemini food scan flag`() {
        assertFalse(
            PremiumAccessPolicy.canUseGeminiFoodScan(
                isPremiumActive = true,
                featureEnabled = false,
                isDebugBuild = false,
                forceUnlockForTesting = false
            )
        )
        assertFalse(
            PremiumAccessPolicy.canUseGeminiFoodScan(
                isPremiumActive = false,
                featureEnabled = true,
                isDebugBuild = false,
                forceUnlockForTesting = false
            )
        )
        assertTrue(
            PremiumAccessPolicy.canUseGeminiFoodScan(
                isPremiumActive = true,
                featureEnabled = true,
                isDebugBuild = false,
                forceUnlockForTesting = false
            )
        )
    }
}
