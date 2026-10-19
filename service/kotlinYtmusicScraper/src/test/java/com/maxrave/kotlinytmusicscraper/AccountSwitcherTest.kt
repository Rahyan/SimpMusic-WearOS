package com.maxrave.kotlinytmusicscraper

import com.maxrave.kotlinytmusicscraper.models.response.AccountSwitcherEndpointResponse
import com.maxrave.kotlinytmusicscraper.models.response.toListAccountInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AccountSwitcherTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun account(name: String, handle: String, extra: String = "") =
        """{"accountItem":{"accountName":$name,"channelHandle":$handle,"accountByline":{}$extra}}"""
    private fun section(items: String, header: String = "") =
        """{"accountSectionListRenderer":{"contents":[{"accountItemSectionRenderer":{"contents":[$items]}}]$header}}"""
    private fun parse(contents: String = "", actions: String = "", code: String = "SUCCESS") =
        json.decodeFromString<AccountSwitcherEndpointResponse>(
            """{"code":"$code","data":{${listOf(contents, actions).filter { it.isNotBlank() }.joinToString(",")}}}""",
        ).toListAccountInfo()
    private fun action(section: String) =
        """"actions":[{"getMultiPageMenuAction":{"menu":{"multiPageMenuRenderer":{"sections":[$section]}}}}]"""

    @Test fun legacySimpleTextAccountsStillDecode() {
        val item = account("""{"simpleText":"Sample account"}""", """{"simpleText":"@sample"}""")
        val result = parse(contents = """"contents":[null,${section(item)}]""")
        assertEquals("Sample account", result.single().name)
        assertEquals("@sample", result.single().email)
    }

    @Test fun actionMenusAndTextRunsDecodeWithoutSimpleText() {
        val item = account("""{"runs":[{"text":"Sample "},{"text":"account"}]}""",
            """{"runs":[{"text":"@sample"}]}""", """, "onBehalfOfParameter":"sample-page"""")
        val result = parse(actions = action(section(item)))
        assertEquals("Sample account", result.single().name)
        assertEquals("@sample", result.single().email)
        assertEquals("sample-page", result.single().pageId)
    }

    @Test fun malformedOrDisabledEntriesDoNotHideHealthyAccounts() {
        val valid = account("""{"simpleText":"Healthy"}""", """{"simpleText":"@healthy"}""")
        val unnamed = account("{}", """{"simpleText":"@missing-name"}""")
        val disabled = account("""{"simpleText":"Disabled"}""", """{"simpleText":"@disabled"}""", """, "isDisabled":true""")
        assertEquals(listOf("Healthy"), parse(actions = action(section("$unnamed,$disabled,$valid"))).map { it.name })
    }

    @Test fun selectedIdentityComesFirstAndRepeatedMenuCopiesAreDeduplicated() {
        val first = account("""{"simpleText":"First"}""", """{"simpleText":"@first"}""")
        val selected = account("""{"simpleText":"Selected"}""", """{"simpleText":"@selected"}""", """, "isSelected":true""")
        val contents = section("$first,$selected")
        assertEquals(listOf("Selected", "First"), parse(""""contents":[$contents]""", action(contents)).map { it.name })
    }

    @Test fun accountsWithoutHandlesUseTheirSectionEmailAndEmptyIdentitiesAreRejected() {
        val item = account("""{"runs":[{"text":"Sample"}]}""", "{}")
        val header = """, "header":{"googleAccountHeaderRenderer":{"email":{"runs":[{"text":"sample@example.com"}]}}}"""
        assertEquals("sample@example.com", parse(actions = action(section(item, header))).single().email)
        assertTrue(parse(actions = action(section(item))).isEmpty())
    }

    @Test fun unsuccessfulOrEmptyResponsesCannotBecomeSignedInAccounts() {
        val item = account("""{"simpleText":"Sample"}""", """{"simpleText":"@sample"}""")
        assertTrue(parse(actions = action(section(item)), code = "ERROR").isEmpty())
        assertTrue(parse().isEmpty())
    }
}
