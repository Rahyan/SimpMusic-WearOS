package com.maxrave.simpmusic.companion.bridge

import org.junit.Assert.*
import org.junit.Test

class LoginCookieGuardTest {
    @Test fun anonymousMusicCookiesDoNotFinishLogin() {
        assertFalse(hasMusicLoginCookie("https://music.youtube.com/", "CONSENT=YES; VISITOR_INFO1_LIVE=x"))
        assertFalse(hasMusicLoginCookie("https://music.youtube.com/", "SAPISID="))
    }
    @Test fun lookalikeOrInsecureOriginsCannotRelayCredentials() {
        assertFalse(hasMusicLoginCookie("https://music.youtube.com.evil.example/", "SAPISID=x"))
        assertFalse(hasMusicLoginCookie("http://music.youtube.com/", "SAPISID=x"))
    }
    @Test fun exactMusicOriginWithEitherAuthCookieIsEligibleForWatchValidation() {
        assertTrue(hasMusicLoginCookie("https://music.youtube.com/", "SAPISID=x"))
        assertTrue(hasMusicLoginCookie("https://music.youtube.com/?a=b", "__Secure-3PAPISID=x"))
    }
}
