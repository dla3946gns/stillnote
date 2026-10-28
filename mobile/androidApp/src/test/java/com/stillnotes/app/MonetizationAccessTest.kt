package com.stillnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationAccessTest {
    @Test
    fun defaultStateNeverShowsAdsOrUnlocksPlus() {
        val state = MonetizationAccess()
        assertFalse(state.hasPlus)
        assertFalse(state.shouldShowListBanner(noteEditorVisible = false, keyboardVisible = false))
    }

    @Test
    fun pendingPurchaseDoesNotGrantPlus() {
        val state = MonetizationAccess(entitlement = PlusEntitlement.PURCHASE_PENDING)
        assertFalse(state.hasPlus)
    }

    @Test
    fun verifiedPurchaseUnlocksPlusAndSuppressesAds() {
        val state = MonetizationAccess(
            entitlement = PlusEntitlement.VERIFIED,
            adSdkReady = true,
            canRequestAds = true
        )
        assertTrue(state.hasPlus)
        assertFalse(state.shouldShowListBanner(noteEditorVisible = false, keyboardVisible = false))
    }

    @Test
    fun adsRequireBothInitializationAndPermission() {
        assertFalse(MonetizationAccess(adSdkReady = true)
            .shouldShowListBanner(noteEditorVisible = false, keyboardVisible = false))
        assertFalse(MonetizationAccess(canRequestAds = true)
            .shouldShowListBanner(noteEditorVisible = false, keyboardVisible = false))
        assertTrue(MonetizationAccess(adSdkReady = true, canRequestAds = true)
            .shouldShowListBanner(noteEditorVisible = false, keyboardVisible = false))
    }

    @Test
    fun neverShowBannerWhileEditingOrTyping() {
        val state = MonetizationAccess(adSdkReady = true, canRequestAds = true)
        assertFalse(state.shouldShowListBanner(noteEditorVisible = true, keyboardVisible = false))
        assertFalse(state.shouldShowListBanner(noteEditorVisible = false, keyboardVisible = true))
        assertFalse(state.shouldShowListBanner(noteEditorVisible = true, keyboardVisible = true))
    }

    @Test
    fun productIdentifierIsStableAndPreviewIsDisabled() {
        assertEquals("stillnote_plus_lifetime", MonetizationConfig.PLUS_LIFETIME_PRODUCT_ID)
        assertFalse(MonetizationConfig.SHOW_PLUS_PREVIEW)
    }
}
