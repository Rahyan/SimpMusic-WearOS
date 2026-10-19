package com.maxrave.kotlinytmusicscraper

import java.io.IOException
import java.net.UnknownHostException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DnsDependencyCompatibilityTest {
    @Test(timeout = 10_000)
    fun failedIpv4AndIpv6QueriesRemainDnsErrors() {
        // This path used to reference missing OkHttp 3 internals when running with OkHttp 5.
        // The interceptor fails both queries before any DNS lookup or network connection.
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Simulated offline connection") }
            .build()
        try {
            val dns = DnsOverHttps.Builder()
                .client(client)
                .url("https://resolver.example/dns-query".toHttpUrl())
                .includeIPv6(true)
                .resolvePrivateAddresses(true)
                .build()
            val error = assertThrows(UnknownHostException::class.java) {
                dns.lookup("track.example")
            }
            assertEquals("Simulated offline connection", error.cause?.message)
            assertEquals(1, error.suppressed.size)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
