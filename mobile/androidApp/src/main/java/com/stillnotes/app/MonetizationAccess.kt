package com.stillnotes.app

/**
 * Store-facing product identifier. Add the matching one-time product in Play
 * Console before integrating BillingClient; never treat a local tap as a purchase.
 */
internal object MonetizationConfig {
    const val PLUS_LIFETIME_PRODUCT_ID = "stillnote_plus_lifetime"

    // Do not advertise or sell an unimplemented product in a production build.
    // A later PR may enable the entry only after Billing and purchase restore work.
    const val SHOW_PLUS_PREVIEW = false
}

internal enum class PlusEntitlement {
    FREE,
    PURCHASE_PENDING,
    VERIFIED
}

/**
 * Independent from NotesStore/NotesStorage so ads and billing cannot change
 * note persistence or require login/cloud sync to use existing free features.
 *
 * A verified entitlement must originate from a Play Billing ownership check,
 * not from local preferences or a purchase button callback.
 */
internal data class MonetizationAccess(
    val entitlement: PlusEntitlement = PlusEntitlement.FREE,
    val adSdkReady: Boolean = false,
    val canRequestAds: Boolean = false
) {
    val hasPlus: Boolean
        get() = entitlement == PlusEntitlement.VERIFIED

    /**
     * Only a real AdMob component may occupy this slot after UMP allows ads.
     * Never show ads in an editor, while an IME is open or to a Plus customer.
     */
    fun shouldShowListBanner(noteEditorVisible: Boolean, keyboardVisible: Boolean): Boolean =
        adSdkReady && canRequestAds && !hasPlus && !noteEditorVisible && !keyboardVisible
}
