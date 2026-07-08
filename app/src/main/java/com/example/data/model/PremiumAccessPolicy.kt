package com.example.data.model

object PremiumAccessPolicy {
    const val FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING: Boolean = true
    const val PREMIUM_ENABLED: Boolean = false

    fun isPremiumActive(
        storedPremiumUnlocked: Boolean,
        isDebugBuild: Boolean,
        forceUnlockForTesting: Boolean = FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING
    ): Boolean = forceUnlockForTesting || isDebugBuild || storedPremiumUnlocked

    fun storedPremiumFromLocalPatch(
        enabled: Boolean,
        isDebugBuild: Boolean
    ): Boolean = enabled && isDebugBuild

    fun canUseGeminiFoodScan(
        isPremiumActive: Boolean,
        featureEnabled: Boolean,
        isDebugBuild: Boolean,
        forceUnlockForTesting: Boolean = FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING
    ): Boolean = forceUnlockForTesting || isDebugBuild || (isPremiumActive && featureEnabled)

    fun shouldShowPremiumEntryPoint(
        isPremiumActive: Boolean,
        premiumEnabled: Boolean = PREMIUM_ENABLED,
        forceUnlockForTesting: Boolean = FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING
    ): Boolean = premiumEnabled && !forceUnlockForTesting && !isPremiumActive
}
