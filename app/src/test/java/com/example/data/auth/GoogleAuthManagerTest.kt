package com.example.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthManagerTest {
    @Test
    fun webClientIdValidationRejectsPlaceholdersAndMalformedValues() {
        assertFalse(isConfiguredGoogleWebClientId(""))
        assertFalse(isConfiguredGoogleWebClientId("MY_GOOGLE_WEB_CLIENT_ID"))
        assertFalse(isConfiguredGoogleWebClientId("placeholder-web-client"))
        assertFalse(isConfiguredGoogleWebClientId("not-a-google-client-id"))
    }

    @Test
    fun webClientIdValidationAcceptsGoogleOAuthClientShape() {
        assertTrue(isConfiguredGoogleWebClientId("1234567890-abcdef.apps.googleusercontent.com"))
    }
}
