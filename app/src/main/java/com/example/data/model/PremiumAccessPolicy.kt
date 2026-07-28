package com.example.data.model

import com.example.BuildConfig

/**
 * Resolves access without mutating purchase state. [premiumTestMode] is an
 * in-memory build capability; [realEntitlement] remains the Billing-owned value.
 */
internal class PremiumEntitlementProvider(
    val premiumTestMode: Boolean
) {
    fun hasPremiumAccess(realEntitlement: Boolean): Boolean =
        premiumTestMode || realEntitlement

    fun canUsePremiumFeature(
        realEntitlement: Boolean,
        featureEnabled: Boolean
    ): Boolean = premiumTestMode || (realEntitlement && featureEnabled)
}

object PremiumAccessPolicy {
    const val PREMIUM_ENABLED: Boolean = false

    private val entitlementProvider = PremiumEntitlementProvider(
        // Defense in depth: a release build cannot enable the QA bypass even if
        // PREMIUM_TEST_MODE were accidentally changed in its Gradle configuration.
        premiumTestMode = BuildConfig.DEBUG && BuildConfig.PREMIUM_TEST_MODE
    )

    val isTestMode: Boolean
        get() = entitlementProvider.premiumTestMode

    fun isPremiumActive(storedPremiumUnlocked: Boolean): Boolean =
        entitlementProvider.hasPremiumAccess(realEntitlement = storedPremiumUnlocked)

    fun canUseGeminiFoodScan(
        isPremiumActive: Boolean,
        featureEnabled: Boolean
    ): Boolean = entitlementProvider.canUsePremiumFeature(
        realEntitlement = isPremiumActive,
        featureEnabled = featureEnabled
    )

    fun shouldShowPremiumEntryPoint(
        isPremiumActive: Boolean,
        premiumEnabled: Boolean = PREMIUM_ENABLED
    ): Boolean = premiumEnabled && !isPremiumActive
}
