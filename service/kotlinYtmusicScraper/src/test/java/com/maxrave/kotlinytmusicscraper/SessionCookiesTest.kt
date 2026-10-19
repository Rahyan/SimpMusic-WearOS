package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.utils.sapisidFromCookieHeader
import org.junit.Assert.*
import org.junit.Test

class SessionCookiesTest {
    @Test fun eitherSapisidVariantCanAuthorizeTheRequest() {
        assertEquals("regular", sapisidFromCookieHeader("OTHER=one; SAPISID=regular"))
        assertEquals("secure=tail", sapisidFromCookieHeader("__Secure-3PAPISID=secure=tail"))
        assertEquals("regular", sapisidFromCookieHeader("SAPISID=regular; __Secure-3PAPISID=secure"))
    }
    @Test fun anonymousOrEmptyValuesCannotAuthorize() {
        assertNull(sapisidFromCookieHeader(null))
        assertNull(sapisidFromCookieHeader("CONSENT=YES; SAPISID=; __Secure-3PAPISID="))
    }
}
