package com.skippy.app

import org.junit.Test
import org.junit.Assert.*

class AuthTest {
    @Test
    fun testIsExpired() {
        val method = Auth::class.java.getDeclaredMethod("isExpired", String::class.java)
        method.isAccessible = true
        
        // Test invalid token (should return false on exception)
        val result = method.invoke(Auth, "invalid_token_without_dots") as Boolean
        assertFalse("Invalid token should not be considered expired (false)", result)
    }
}
