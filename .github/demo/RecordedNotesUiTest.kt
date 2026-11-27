package com.stillnotes.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordedNotesUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun recordDemo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("고정된 메모").assertIsDisplayed()
            compose.onNodeWithText("최근 메모").assertIsDisplayed()
            holdFrame()

            compose.onNodeWithText("검색").performClick()
            compose.onNodeWithTag("notes-search").performTextReplacement("Weekend")
            compose.onNodeWithText("주말 계획").assertIsDisplayed()
            holdFrame()

            compose.onNodeWithText("주말 계획").performClick()
            val body = compose.onNodeWithTag("note-body")
            body.assertTextContains("Weekend", substring = true)
            holdFrame()

            val edited = "토요일에는 동네 카페에서 책 읽기. Weekend\n일요일에는 산책하기."
            body.performTextReplacement(edited)
            body.assertTextContains("일요일에는 산책하기.", substring = true)
            holdFrame()
            compose.onNodeWithContentDescription("본문 되돌리기").performClick()
            body.assertTextContains("토요일에는 동네 카페에서 책 읽기. Weekend")
            holdFrame()
            compose.onNodeWithContentDescription("본문 다시 실행").performClick()
            body.assertTextContains(edited)
            holdFrame()

            compose.onNodeWithText("저장").performClick()
            compose.onNodeWithText("홈").performClick()
            compose.onNodeWithText("주말 계획").assertIsDisplayed()
            holdFrame()
        }
    }

    private fun holdFrame() {
        compose.waitForIdle()
        Thread.sleep(1800)
    }
}
