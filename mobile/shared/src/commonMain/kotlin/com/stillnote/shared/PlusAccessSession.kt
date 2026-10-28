package com.stillnote.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The server API is the authority for Plus access.
 * This is only a shared client state model; it does not authenticate a person
 * or validate a Google Play / App Store purchase.
 */
enum class PlusAccessStatus {
    SIGNED_OUT,
    CHECKING,
    FREE,
    PLUS,
    TEMPORARILY_UNAVAILABLE
}

enum class PlusLookupResult {
    FREE,
    PLUS,
    TEMPORARILY_UNAVAILABLE
}

/** In-flight account-specific request. Responses from older sessions are ignored. */
data class PlusEntitlementCheck(val requestId: Long, val firebaseUid: String)

/**
 * The Android and iOS login clients will call beginCheck after acquiring a
 * Firebase-authenticated UID; they must never trust a user-entered UID.
 *
 * This object is UI-thread confined, like NotesStore. It never stores notes
 * or changes notes/backup availability.
 */
class PlusAccessSession {
    private var requestSequence = 0L
    private var activeUid: String? = null

    var status: PlusAccessStatus = PlusAccessStatus.SIGNED_OUT
        private set

    val hasPlus: Boolean
        get() = status == PlusAccessStatus.PLUS

    fun beginCheck(firebaseUid: String): PlusEntitlementCheck {
        require(firebaseUid.isNotBlank()) { "Authenticated Firebase UID required" }
        requestSequence++
        activeUid = firebaseUid
        status = PlusAccessStatus.CHECKING
        return PlusEntitlementCheck(requestSequence, firebaseUid)
    }

    /**
     * Accept only the latest response for the same authenticated account.
     * Sign-out, a new request, or an account switch invalidates old responses.
     */
    fun completeCheck(check: PlusEntitlementCheck, result: PlusLookupResult): Boolean {
        if (status != PlusAccessStatus.CHECKING ||
            requestSequence != check.requestId ||
            activeUid != check.firebaseUid
        ) return false

        status = when (result) {
            PlusLookupResult.FREE -> PlusAccessStatus.FREE
            PlusLookupResult.PLUS -> PlusAccessStatus.PLUS
            PlusLookupResult.TEMPORARILY_UNAVAILABLE -> PlusAccessStatus.TEMPORARILY_UNAVAILABLE
        }
        return true
    }

    fun signOut() {
        requestSequence++
        activeUid = null
        status = PlusAccessStatus.SIGNED_OUT
    }
}

/**
 * Parse only a successful response from GET /v1/entitlement. Do not silently
 * convert a server/network outage or malformed payload into a free account.
 */
object PlusEntitlementResponse {
    fun parse(statusCode: Int, body: String): PlusLookupResult {
        if (statusCode != 200) return PlusLookupResult.TEMPORARILY_UNAVAILABLE
        return try {
            val payload = Json.parseToJsonElement(body) as? JsonObject
                ?: return PlusLookupResult.TEMPORARILY_UNAVAILABLE
            val kind = payload["entitlement"] as? JsonPrimitive
                ?: return PlusLookupResult.TEMPORARILY_UNAVAILABLE
            val flag = payload["hasPlus"] as? JsonPrimitive
                ?: return PlusLookupResult.TEMPORARILY_UNAVAILABLE
            if (!kind.isString || flag.isString) {
                return PlusLookupResult.TEMPORARILY_UNAVAILABLE
            }
            when {
                kind.content == "plus" && flag.booleanOrNull == true -> PlusLookupResult.PLUS
                kind.content == "free" && flag.booleanOrNull == false -> PlusLookupResult.FREE
                else -> PlusLookupResult.TEMPORARILY_UNAVAILABLE
            }
        } catch (_: Exception) {
            PlusLookupResult.TEMPORARILY_UNAVAILABLE
        }
    }
}
