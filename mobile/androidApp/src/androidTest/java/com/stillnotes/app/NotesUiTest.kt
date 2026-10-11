package com.stillnotes.app

import android.content.Context
import android.content.res.Configuration
import android.view.WindowInsets
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI and real-device-storage regressions running on an Android emulator.
 *
 * Each test launches a fresh Activity with empty local files. The CI job runs this
 * same suite with both Android light and dark system appearances.
 */
@RunWith(AndroidJUnit4::class)
class NotesUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun startClean() {
        val directory = targetContext().filesDir
        check(File(directory, "stillnote-notes.json").let { !it.exists() || it.delete() })
        check(File(directory, "stillnote-notes.json.tmp").let { !it.exists() || it.delete() })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }


    private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isKeyboardVisible(): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = (activity.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0) > 0
        }
        return visible
    }

    private fun captureKeyboardLayout(name: String) {
        val night = (targetContext().resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = checkNotNull(targetContext().getExternalFilesDir("qa"))
        assertTrue(directory.exists() || directory.mkdirs())
        val output = File(directory, name + "-" + (if (night) "dark" else "light") + ".png")
        output.outputStream().use {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }


    private fun newDraft(title: String, body: String) {
        compose.onNodeWithText("새 메모").performClick()
        compose.onNodeWithTag("note-title").performTextInput(title)
        compose.onNodeWithTag("note-body").performTextInput(body)
        compose.waitForIdle()
    }

    private fun saveDraft() {
        compose.onNodeWithText("저장").performClick()
        compose.waitForIdle()
    }

    private fun saveNew(title: String, body: String) {
        newDraft(title, body)
        saveDraft()
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun explicitSaveSurvivesClosingAndReopeningTheActivity() {
        newDraft("재실행 테스트", "다시 열어도 남는 본문")
        compose.onNodeWithText("미저장").assertIsDisplayed()
        saveDraft()

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        compose.onNodeWithText("재실행 테스트").performClick()
        compose.onNodeWithTag("note-title").assertIsDisplayed()
        compose.onNodeWithTag("note-body").assertExists()
        compose.onNodeWithTag("note-title").assertTextContains("재실행 테스트")
        compose.onNodeWithTag("note-body").assertTextContains("다시 열어도 남는 본문")
    }

    @Test
    fun cancelExistingEditAndDiscardNewDraftDoNotChangeSavedNotes() {
        saveNew("원본 제목", "원본 본문")
        compose.onNodeWithText("원본 제목").performClick()
        compose.onNodeWithTag("note-title").performTextReplacement("수정 중 제목")
        compose.onNodeWithText("취소").performClick()

        compose.onNodeWithText("원본 제목").assertIsDisplayed()
        compose.onNodeWithText("수정 중 제목").assertDoesNotExist()

        newDraft("저장하지 않을 메모", "취소할 본문")
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("원본 제목").assertIsDisplayed()
        compose.onNodeWithText("저장하지 않을 메모").assertDoesNotExist()
        compose.onNodeWithText("1개").assertIsDisplayed()
    }

    @Test
    fun unsavedNavigationOffersContinueEditingSaveAndDiscard() {
        newDraft("확인창 테스트", "내용")
        compose.onNodeWithText("목록").performClick()
        compose.onNodeWithText("변경 내용을 저장할까요?").assertIsDisplayed()
        compose.onNodeWithText("계속 편집").performClick()
        compose.onNodeWithTag("note-title").assertTextContains("확인창 테스트")

        compose.onNodeWithText("목록").performClick()
        compose.onNodeWithTag("unsaved-dialog-save").performClick()
        compose.onNodeWithText("확인창 테스트").assertExists()

        newDraft("버릴 초안", "임시 내용")
        compose.onNodeWithText("목록").performClick()
        compose.onNodeWithText("버리기").performClick()
        compose.onNodeWithText("확인창 테스트").assertExists()
        compose.onNodeWithText("버릴 초안").assertDoesNotExist()
    }

    @Test
    fun titleAndBodySearchFilterRealSavedNotes() {
        saveNew("첫 번째 메모", "여행 계획")
        saveNew("둘째 메모", "커피 기록")

        compose.onNodeWithTag("notes-search").performTextInput("여행")
        compose.onNodeWithText("첫 번째 메모").assertExists()
        compose.onNodeWithText("둘째 메모").assertDoesNotExist()

        compose.onNodeWithTag("notes-search").performTextReplacement("없는 검색어")
        compose.waitForIdle()
        // The software keyboard may obscure the empty-state message. Verify the
        // filtered list is truly empty rather than relying on on-screen visibility.
        compose.onNodeWithText("0개").assertExists()
        compose.onNodeWithText("검색 결과가 없어요").assertExists()
        compose.onNodeWithText("첫 번째 메모").assertDoesNotExist()
        compose.onNodeWithText("둘째 메모").assertDoesNotExist()

        compose.onNodeWithTag("notes-search").performTextReplacement("")
        compose.waitForIdle()
        // LazyColumn only exposes composed (viewport) rows while the IME is up.
        // Verify the full result count and the disappearance of the empty state;
        // inspecting offscreen row semantics would make this test flaky.
        compose.onNodeWithText("2개").assertExists()
        compose.onNodeWithText("검색 결과가 없어요").assertDoesNotExist()
    }

    @Test
    fun pinFilterKeepsOnlyPinnedNotes() {
        saveNew("고정할 메모", "고정 내용")
        saveNew("일반 메모", "일반 내용")
        compose.onNodeWithText("고정할 메모").performClick()
        compose.onNodeWithText("메모 메뉴").performClick()
        compose.onNodeWithText("메모 고정").performClick()
        compose.onNodeWithText("목록").performClick()

        compose.onNodeWithText("고정 메모").performClick()
        compose.onNodeWithText("고정할 메모").assertIsDisplayed()
        compose.onNodeWithText("일반 메모").assertDoesNotExist()
        compose.onNodeWithText("1개").assertIsDisplayed()
    }

    @Test
    fun deletionRequiresConfirmationAndUndoRestoresTheNote() {
        saveNew("삭제 확인 메모", "복원할 본문")
        compose.onNodeWithText("삭제 확인 메모").performClick()
        compose.onNodeWithText("메모 메뉴").performClick()
        compose.onNodeWithText("메모 삭제").performClick()
        compose.onNodeWithText("메모를 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithTag("delete-dialog-cancel").performClick()
        compose.onNodeWithTag("note-title").assertTextContains("삭제 확인 메모")

        compose.onNodeWithText("메모 메뉴").performClick()
        compose.onNodeWithText("메모 삭제").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("아직 메모가 없어요").assertIsDisplayed()
        compose.onNodeWithText("삭제 취소").performClick()
        compose.onNodeWithText("삭제 확인 메모").assertIsDisplayed()
    }


    @Test
    fun searchAndEditorRemainUsableWithTheKeyboardOpen() {
        // Regression for a collapsed UI with adjustResize and a visible IME.
        compose.onNodeWithTag("notes-search").performClick()
        compose.waitUntil(10_000) { isKeyboardVisible() }
        compose.onNodeWithTag("notes-search").assertIsDisplayed()
        compose.onNodeWithTag("notes-search").performTextInput("검색 키보드")
        compose.onNodeWithTag("notes-search").assertIsDisplayed()
        captureKeyboardLayout("keyboard-list")

        compose.onNodeWithText("새 메모").performClick()
        compose.onNodeWithTag("note-title").performClick()
        compose.waitUntil(10_000) { isKeyboardVisible() }
        compose.onNodeWithTag("note-title").assertIsDisplayed()
        compose.onNodeWithTag("note-title").performTextInput("키보드 레이아웃")
        compose.onNodeWithTag("note-title").assertIsDisplayed()

        compose.onNodeWithTag("note-body").performScrollTo().performClick()
        compose.waitUntil(10_000) { isKeyboardVisible() }
        compose.onNodeWithTag("note-body").assertIsDisplayed()
        compose.onNodeWithTag("note-body").performTextInput("본문 입력을 확인합니다.")
        compose.onNodeWithTag("note-body").assertIsDisplayed()
        captureKeyboardLayout("keyboard-editor")

        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(10_000) { !isKeyboardVisible() }
        compose.onNodeWithTag("notes-search").assertIsDisplayed()
        compose.onNodeWithText("키보드 레이아웃").assertExists()
    }

    @Test
    fun appRendersCorrectBackgroundInCurrentLightOrDarkMode() {
        compose.onNodeWithText("Stillnote").assertIsDisplayed()
        val night = (targetContext().resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(screenshot.width > 10 && screenshot.height > 10)

        // Left margin of the notes list is painted directly with the app background color.
        val expected = if (night) 0xFF241F1A.toInt() else 0xFFF5EFE3.toInt()
        assertEquals("Notes list background should match system theme", expected,
            screenshot.getPixel(3, screenshot.height / 2))

        val output = File(targetContext().getExternalFilesDir("qa"), "notes-list-${if (night) "dark" else "light"}.png")
        checkNotNull(output.parentFile).mkdirs()
        output.outputStream().use { stream ->
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
