package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumAccessPolicyTest {
    @Test
    fun `premium test mode grants access without a real entitlement`() {
        val provider = PremiumEntitlementProvider(premiumTestMode = true)

        assertTrue(provider.hasPremiumAccess(realEntitlement = false))
    }

    @Test
    fun `release mode remains free without a real entitlement`() {
        val provider = PremiumEntitlementProvider(premiumTestMode = false)

        assertFalse(provider.hasPremiumAccess(realEntitlement = false))
    }

    @Test
    fun `real billing entitlement remains active when test mode is disabled`() {
        val provider = PremiumEntitlementProvider(premiumTestMode = false)

        assertTrue(provider.hasPremiumAccess(realEntitlement = true))
    }

    @Test
    fun `fresh debug provider keeps QA access after an app restart without stored entitlement`() {
        val providerAfterRestart = PremiumEntitlementProvider(premiumTestMode = true)

        assertTrue(providerAfterRestart.hasPremiumAccess(realEntitlement = false))
    }

    @Test
    fun `premium access decision is local and does not require a remote provider`() {
        val offlineProvider = PremiumEntitlementProvider(premiumTestMode = true)

        assertTrue(offlineProvider.hasPremiumAccess(realEntitlement = false))
    }

    @Test
    fun `test mode can exercise a premium feature while its remote flag is disabled`() {
        val provider = PremiumEntitlementProvider(premiumTestMode = true)

        assertTrue(
            provider.canUsePremiumFeature(
                realEntitlement = false,
                featureEnabled = false
            )
        )
    }

    @Test
    fun `production feature requires both real entitlement and feature availability`() {
        val provider = PremiumEntitlementProvider(premiumTestMode = false)

        assertFalse(provider.canUsePremiumFeature(realEntitlement = true, featureEnabled = false))
        assertFalse(provider.canUsePremiumFeature(realEntitlement = false, featureEnabled = true))
        assertTrue(provider.canUsePremiumFeature(realEntitlement = true, featureEnabled = true))
    }
}
