package com.makd.afinity.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun extractHostHandlesSchemesPortsUserinfoPathsAndIpv6() {
        assertEquals("example.com", extractHost("https://example.com:8096/jellyfin"))
        assertEquals("example.com", extractHost("  HTTP://User:Pass@Example.COM:8080/path "))
        assertEquals("192.168.1.10", extractHost("http://192.168.1.10:8096"))
        assertEquals("::1", extractHost("http://[::1]:8096/web"))
        assertEquals("fd7a:115c:a1e0::1", extractHost("https://[FD7A:115C:A1E0::1]:443"))
    }

    @Test
    fun isLocalAddressMatchesLoopbackPrivateRfc1918Ipv6AndLocalNames() {
        assertTrue(isLocalAddress("http://localhost:8096"))
        assertTrue(isLocalAddress("http://localhost.:8096"))
        assertTrue(isLocalAddress("http://jellyfin"))
        assertTrue(isLocalAddress("http://nas.local:8096"))
        assertTrue(isLocalAddress("http://media.lan"))
        assertTrue(isLocalAddress("http://box.home.arpa"))
        assertTrue(isLocalAddress("http://app.internal"))
        assertTrue(isLocalAddress("http://host.localdomain"))
        assertTrue(isLocalAddress("http://127.0.0.1:8096"))
        assertTrue(isLocalAddress("http://169.254.1.1"))
        assertTrue(isLocalAddress("http://192.168.0.50:8096"))
        assertTrue(isLocalAddress("http://10.0.0.5:8096"))
        assertTrue(isLocalAddress("http://172.16.0.1:8096"))
        assertTrue(isLocalAddress("http://172.31.255.254:8096"))
        assertTrue(isLocalAddress("http://[::1]:8096"))
        assertTrue(isLocalAddress("http://[fe80::1]:8096"))
        assertTrue(isLocalAddress("http://[fc00::1]:8096"))
        assertTrue(isLocalAddress("http://[fd12:3456::1]:8096"))
    }

    @Test
    fun isLocalAddressRejectsPublicAndOutOfRangeHosts() {
        assertFalse(isLocalAddress("https://media.example.com"))
        assertFalse(isLocalAddress("http://172.15.0.1:8096"))
        assertFalse(isLocalAddress("http://172.32.0.1:8096"))
        assertFalse(isLocalAddress("http://8.8.8.8:8096"))
        assertFalse(isLocalAddress("http://[2001:db8::1]:8096"))
        assertFalse(isLocalAddress(""))
    }

    @Test
    fun isTailscaleAddressMatchesDomainsUlaAndCgnatRange() {
        assertTrue(isTailscaleAddress("https://server.tail1234.ts.net"))
        assertTrue(isTailscaleAddress("http://[fd7a:115c:a1e0::42]:8096"))
        assertTrue(isTailscaleAddress("http://100.64.0.1:8096"))
        assertTrue(isTailscaleAddress("http://100.100.100.100:8096"))
        assertTrue(isTailscaleAddress("http://100.127.255.254:8096"))

        assertFalse(isTailscaleAddress("http://100.63.255.254:8096"))
        assertFalse(isTailscaleAddress("http://100.128.0.1:8096"))
        assertFalse(isTailscaleAddress("http://100.bad.0.1:8096"))
        assertFalse(isTailscaleAddress("https://ts.net.example.com"))
    }

    @Test
    fun isInsecurePublicUrlFlagsOnlyPlainHttpToPublicHosts() {
        assertTrue(isInsecurePublicUrl("http://media.example.com:8096"))
        assertTrue(isInsecurePublicUrl("  HTTP://8.8.8.8/jellyfin  "))

        assertFalse(isInsecurePublicUrl("https://media.example.com"))
        assertFalse(isInsecurePublicUrl("http://192.168.1.20:8096"))
        assertFalse(isInsecurePublicUrl("http://nas.local:8096"))
        assertFalse(isInsecurePublicUrl("http://100.80.1.2:8096"))
        assertFalse(isInsecurePublicUrl("http://box.tail123.ts.net:8096"))
        assertFalse(isInsecurePublicUrl("media.example.com"))
    }
}
