package com.example.android.viewmodel.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsLikelyEmailTest {

    @Test
    fun `accepts standard email`() {
        assertTrue(isLikelyEmail("user@example.com"))
    }

    @Test
    fun `accepts email with subdomain`() {
        assertTrue(isLikelyEmail("user.name@mail.example.co.uk"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertTrue(isLikelyEmail("  user@example.com  "))
    }

    @Test
    fun `rejects blank`() {
        assertFalse(isLikelyEmail(""))
        assertFalse(isLikelyEmail("   "))
    }

    @Test
    fun `rejects missing at sign`() {
        assertFalse(isLikelyEmail("userexample.com"))
    }

    @Test
    fun `rejects multiple at signs`() {
        assertFalse(isLikelyEmail("user@@example.com"))
        assertFalse(isLikelyEmail("a@b@c"))
    }

    @Test
    fun `rejects empty local or domain`() {
        assertFalse(isLikelyEmail("@example.com"))
        assertFalse(isLikelyEmail("user@"))
    }
}
