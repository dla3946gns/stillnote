package com.stillnote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlusAccessSessionTest {
    @Test
    fun guestCanUseFreeAppWithoutAPlusSession() {
        val session = PlusAccessSession()
        assertEquals(PlusAccessStatus.SIGNED_OUT, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun verifiedServerResponseCanEnablePlus() {
        val session = PlusAccessSession()
        val check = session.beginCheck("firebase-uid-a")
        assertEquals(PlusAccessStatus.CHECKING, session.status)
        assertTrue(session.completeCheck(check, PlusLookupResult.PLUS))
        assertEquals(PlusAccessStatus.PLUS, session.status)
        assertTrue(session.hasPlus)
    }

    @Test
    fun freeAndUnavailableAreDistinctResults() {
        val session = PlusAccessSession()
        var check = session.beginCheck("firebase-uid-a")
        assertTrue(session.completeCheck(check, PlusLookupResult.FREE))
        assertEquals(PlusAccessStatus.FREE, session.status)
        check = session.beginCheck("firebase-uid-a")
        assertTrue(session.completeCheck(check, PlusLookupResult.TEMPORARILY_UNAVAILABLE))
        assertEquals(PlusAccessStatus.TEMPORARILY_UNAVAILABLE, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun signOutRevokesCachedPlusAndRejectsLateResponse() {
        val session = PlusAccessSession()
        val old = session.beginCheck("firebase-uid-a")
        session.signOut()
        assertEquals(PlusAccessStatus.SIGNED_OUT, session.status)
        assertFalse(session.completeCheck(old, PlusLookupResult.PLUS))
        assertFalse(session.hasPlus)
    }

    @Test
    fun switchingAccountsNeverGrantsNewAccountOldPlus() {
        val session = PlusAccessSession()
        val old = session.beginCheck("firebase-uid-a")
        val latest = session.beginCheck("firebase-uid-b")
        assertFalse(session.completeCheck(old, PlusLookupResult.PLUS))
        assertEquals(PlusAccessStatus.CHECKING, session.status)
        assertTrue(session.completeCheck(latest, PlusLookupResult.FREE))
        assertFalse(session.hasPlus)
    }

    @Test
    fun olderNetworkResponsesCannotOverwriteNewerResponses() {
        val session = PlusAccessSession()
        val old = session.beginCheck("firebase-uid-a")
        val latest = session.beginCheck("firebase-uid-a")
        assertFalse(session.completeCheck(old, PlusLookupResult.PLUS))
        assertTrue(session.completeCheck(latest, PlusLookupResult.FREE))
        assertFalse(session.completeCheck(old, PlusLookupResult.PLUS))
        assertEquals(PlusAccessStatus.FREE, session.status)
    }

    @Test
    fun uidIsRequiredBeforeRequest() {
        assertFailsWith<IllegalArgumentException> { PlusAccessSession().beginCheck(" ") }
    }

    @Test
    fun parsesOnlyConsistentSuccessfulServerResponses() {
        assertEquals(PlusLookupResult.PLUS,
            PlusEntitlementResponse.parse(200, """{"entitlement":"plus","hasPlus":true}"""))
        assertEquals(PlusLookupResult.FREE,
            PlusEntitlementResponse.parse(200, """{"entitlement":"free","hasPlus":false}"""))
    }

    @Test
    fun malformedOrContradictoryResponsesNeverGrantPlus() {
        val bad = listOf(
            """{"entitlement":"plus","hasPlus":false}""",
            """{"entitlement":"free","hasPlus":true}""",
            """{"entitlement":"plus","hasPlus":"true"}""",
            """{"hasPlus":true}""",
            """{"entitlement":"premium","hasPlus":true}""",
            """[]""", """{"entitlement":"plus"}""", """invalid-json"""
        )
        bad.forEach { body ->
            assertEquals(PlusLookupResult.TEMPORARILY_UNAVAILABLE, PlusEntitlementResponse.parse(200, body))
        }
    }

    @Test
    fun statusCodesOtherThan200NeverGrantPlus() {
        val plusBody = """{"entitlement":"plus","hasPlus":true}"""
        listOf(401, 403, 404, 429, 500, 503).forEach { httpStatus ->
            assertEquals(PlusLookupResult.TEMPORARILY_UNAVAILABLE,
                PlusEntitlementResponse.parse(httpStatus, plusBody))
        }
    }
}
