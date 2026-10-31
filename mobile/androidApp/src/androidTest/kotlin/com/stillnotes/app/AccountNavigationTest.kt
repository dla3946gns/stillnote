package com.stillnotes.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun accountEntryValidationAndGuestReturn() {
        // Bind the default Auth instance before opening account UI. No production auth requests.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
            FirebaseAuth.getInstance().signOut()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("더보기").performClick()
            compose.onNodeWithText("계정", substring = false).performClick()
            compose.onNodeWithTag("auth-email").performTextInput("not-an-email")
            compose.onNodeWithTag("auth-password").performTextInput("synthetic-test-input")
            compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
            compose.onNodeWithText("이메일 주소를 확인해 주세요.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("비밀번호를 잊었나요?").performScrollTo().performClick()
            compose.onNodeWithText("재설정 링크 받기").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("로그인 없이 계속 쓰기").performScrollTo().performClick()
            compose.onNodeWithText("새 메모").assertIsDisplayed()
        }
    }
}
