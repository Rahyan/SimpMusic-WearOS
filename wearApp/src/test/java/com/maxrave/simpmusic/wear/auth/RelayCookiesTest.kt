package com.maxrave.simpmusic.wear.auth

import org.junit.Assert.*
import org.junit.Test

class RelayCookiesTest {
    @Test fun preservesValuesAndSessionExpiryWithoutWebView() {
        val result = relayCookiesToNetscape("SID=abc=def; __Secure-3PAPISID=xyz; invalid")
        assertTrue(result.contains(".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tabc=def\n"))
        assertTrue(result.contains("__Secure-3PAPISID\txyz\n"))
        assertFalse(result.contains("invalid"))
    }
    @Test fun rejectsCookieFileRowInjection() {
        val result = relayCookiesToNetscape("safe=value; unsafe=value\ninjected\trow")
        assertTrue(result.contains("safe\tvalue"))
        assertFalse(result.contains("injected"))
    }
}
