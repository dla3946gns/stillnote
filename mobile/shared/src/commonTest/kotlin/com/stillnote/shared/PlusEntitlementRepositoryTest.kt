package com.stillnote.shared

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlusEntitlementRepositoryTest {
    /** Dependency-free runner: test transports return without suspending. */
    private fun <T> runNow(block: suspend () -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        })
        return checkNotNull(completed) { "Test request unexpectedly suspended" }.getOrThrow()
    }

    @Test
    fun verifiedServerPlusGrantsAccessAndPassesTokenToTransport() {
        val session = PlusAccessSession()
        var suppliedToken: String? = null
        val transport = PlusEntitlementTransport { token ->
            suppliedToken = token
            PlusEntitlementHttpResponse(200, """{"entitlement":"plus","hasPlus":true}""")
        }
        val repository = PlusEntitlementRepository(session, transport)

        assertTrue(runNow { repository.refresh("uid-a", "firebase-token-a") })
        assertEquals("firebase-token-a", suppliedToken)
        assertEquals(PlusAccessStatus.PLUS, session.status)
        assertTrue(session.hasPlus)
    }

    @Test
    fun verifiedFreeIsNotAnOutage() {
        val session = PlusAccessSession()
        val repository = PlusEntitlementRepository(
            session,
            PlusEntitlementTransport {
                PlusEntitlementHttpResponse(200, """{"entitlement":"free","hasPlus":false}""")
            },
        )

        assertTrue(runNow { repository.refresh("uid-a", "token") })
        assertEquals(PlusAccessStatus.FREE, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun unauthorizedOrFailedServerIsNotMisclassifiedAsFree() {
        for (statusCode in listOf(401, 403, 429, 500, 503)) {
            val session = PlusAccessSession()
            val repository = PlusEntitlementRepository(
                session,
                PlusEntitlementTransport {
                    PlusEntitlementHttpResponse(statusCode, """{"entitlement":"plus","hasPlus":true}""")
                },
            )
            assertTrue(runNow { repository.refresh("uid-a", "token") })
            assertEquals(PlusAccessStatus.TEMPORARILY_UNAVAILABLE, session.status)
            assertFalse(session.hasPlus)
        }
    }

    @Test
    fun networkFailureDoesNotGrantPlusOrChangeNotes() {
        val session = PlusAccessSession()
        val repository = PlusEntitlementRepository(
            session, PlusEntitlementTransport { throw IllegalStateException("offline") },
        )

        assertTrue(runNow { repository.refresh("uid-a", "token") })
        assertEquals(PlusAccessStatus.TEMPORARILY_UNAVAILABLE, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun cancellationIsRethrownAndClearsPendingState() {
        val session = PlusAccessSession()
        val repository = PlusEntitlementRepository(
            session, PlusEntitlementTransport { throw CancellationException("cancelled") },
        )
        assertFailsWith<CancellationException> {
            runNow { repository.refresh("uid-a", "token") }
        }
        assertEquals(PlusAccessStatus.TEMPORARILY_UNAVAILABLE, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun signOutDuringRequestRejectsLatePlusResponse() {
        val session = PlusAccessSession()
        lateinit var repository: PlusEntitlementRepository
        repository = PlusEntitlementRepository(
            session,
            PlusEntitlementTransport {
                repository.signOut()
                PlusEntitlementHttpResponse(200, """{"entitlement":"plus","hasPlus":true}""")
            },
        )

        assertFalse(runNow { repository.refresh("uid-a", "token") })
        assertEquals(PlusAccessStatus.SIGNED_OUT, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun accountChangeDuringRequestCannotGrantPreviousUsersPlus() {
        val session = PlusAccessSession()
        val repository = PlusEntitlementRepository(
            session,
            PlusEntitlementTransport {
                session.beginCheck("uid-b")
                PlusEntitlementHttpResponse(200, """{"entitlement":"plus","hasPlus":true}""")
            },
        )

        assertFalse(runNow { repository.refresh("uid-a", "token-a") })
        assertEquals(PlusAccessStatus.CHECKING, session.status)
        assertFalse(session.hasPlus)
    }

    @Test
    fun blankUidOrTokenNeverReachesNetwork() {
        val session = PlusAccessSession()
        var requests = 0
        val repository = PlusEntitlementRepository(
            session,
            PlusEntitlementTransport {
                requests++
                PlusEntitlementHttpResponse(200, """{"entitlement":"plus","hasPlus":true}""")
            },
        )
        assertFailsWith<IllegalArgumentException> {
            runNow { repository.refresh("", "token") }
        }
        assertFailsWith<IllegalArgumentException> {
            runNow { repository.refresh("uid-a", " ") }
        }
        assertEquals(0, requests)
        assertEquals(PlusAccessStatus.SIGNED_OUT, session.status)
    }

    @Test
    fun contradictorySuccessfulResponseNeverGrantsPlus() {
        val session = PlusAccessSession()
        val repository = PlusEntitlementRepository(
            session,
            PlusEntitlementTransport {
                PlusEntitlementHttpResponse(200, """{"entitlement":"plus","hasPlus":false}""")
            },
        )
        assertTrue(runNow { repository.refresh("uid-a", "token") })
        assertEquals(PlusAccessStatus.TEMPORARILY_UNAVAILABLE, session.status)
        assertFalse(session.hasPlus)
    }
}
