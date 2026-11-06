package com.stillnote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailAuthInputTest {
    @Test
    fun registrationRequiresAgeConfirmationWithoutChangingLoginOrReset() {
        assertEquals(AuthProblem.AGE_CONFIRMATION_REQUIRED,
            EmailAuthInput.validateRegistration("name@example.com", "password", false))
        assertNull(EmailAuthInput.validateRegistration("name@example.com", "password", true))
        assertEquals(AuthProblem.INVALID_EMAIL, EmailAuthInput.validateRegistration("invalid", "password", true))
        assertEquals(AuthProblem.MISSING_PASSWORD, EmailAuthInput.validateRegistration("name@example.com", "", true))
        assertNull(EmailAuthInput.validate("name@example.com", "password"))
        assertNull(EmailAuthInput.validate("name@example.com", "", passwordRequired = false))
    }

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
