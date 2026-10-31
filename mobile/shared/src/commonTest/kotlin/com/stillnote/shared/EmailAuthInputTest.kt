package com.stillnote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailAuthInputTest {
    @Test
    fun rejectsMissingPartsAndMultipleSeparators() {
        for (email in listOf("", "name", "@example.com", "name@", "name@@example.com", "a b@example.com")) {
            assertEquals(AuthProblem.INVALID_EMAIL, EmailAuthInput.validate(email, "password"))
        }
    }

    @Test
    fun trimsOnlyEmailAndLeavesExistingPasswordPolicyToProvider() {
        assertEquals("Name@example.com", EmailAuthInput.normalizedEmail("  Name@example.com  "))
        assertNull(EmailAuthInput.validate("  Name@example.com  ", " x "))
        assertNull(EmailAuthInput.validate("name@example.com", " "))
        assertEquals(AuthProblem.MISSING_PASSWORD, EmailAuthInput.validate("name@example.com", ""))
    }

    @Test
    fun resetDoesNotRequirePassword() {
        assertNull(EmailAuthInput.validate("name@example.com", "", passwordRequired = false))
        assertEquals(AuthProblem.INVALID_EMAIL, EmailAuthInput.validate("", "", passwordRequired = false))
    }
}
