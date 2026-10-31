package com.stillnote.shared

import kotlin.coroutines.cancellation.CancellationException

/**
 * Response metadata from a trusted, platform-provided HTTPS transport.
 *
 * The Android/iOS adapter must send GET /v1/entitlement to the configured
 * Cloudflare Workers endpoint with Authorization: Bearer <Firebase ID token>.
 * It must use TLS, avoid redirects to untrusted origins, bound response sizes,
 * and never log the token or response body.
 */
data class PlusEntitlementHttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Network boundary only. Authentication, platform networking, and UI remain
 * outside this KMP common business-logic module.
 */
fun interface PlusEntitlementTransport {
    suspend fun getEntitlement(firebaseIdToken: String): PlusEntitlementHttpResponse
}

/**
 * Connects a Firebase-authenticated account to the shared Plus access model.
 *
 * Callers must obtain both firebaseUid and firebaseIdToken from the SAME
 * authenticated Firebase SDK session, refresh the ID token when required, and
 * call signOut() when the user logs out or switches accounts.
 *
 * Never accept a manually entered UID or a client-side store purchase as proof
 * of Plus. Only a consistent, successful entitlement server response can grant
 * Plus. This class has no offline entitlement cache or paid-feature UI logic.
 *
 * Like PlusAccessSession, call this on one UI/application dispatcher; any
 * resumed continuation must return to that dispatcher before mutating state.
 */
class PlusEntitlementRepository(
    val session: PlusAccessSession,
    private val transport: PlusEntitlementTransport,
) {
    /**
     * Returns true only when the response was applied to the current session.
     * Old responses are discarded after sign-out, account switch, or newer
     * refresh. A network/server error leaves the state temporarily unavailable,
     * not FREE and not PLUS.
     */
    suspend fun refresh(firebaseUid: String, firebaseIdToken: String): Boolean {
        require(firebaseUid.isNotBlank()) { "Authenticated Firebase UID required" }
        require(firebaseIdToken.isNotBlank()) { "Firebase ID token required" }

        val check = session.beginCheck(firebaseUid)
        val result = try {
            val response = transport.getEntitlement(firebaseIdToken)
            PlusEntitlementResponse.parse(response.statusCode, response.body)
        } catch (cancelled: CancellationException) {
            // Preserve structured cancellation; never retain a stale PLUS state.
            session.completeCheck(check, PlusLookupResult.TEMPORARILY_UNAVAILABLE)
            throw cancelled
        } catch (_: Exception) {
            // Fail closed and do not log credentials or opaque server payloads.
            PlusLookupResult.TEMPORARILY_UNAVAILABLE
        }

        return session.completeCheck(check, result)
    }

    /** Invalidates any in-flight request and clears access immediately. */
    fun signOut() {
        session.signOut()
    }
}
