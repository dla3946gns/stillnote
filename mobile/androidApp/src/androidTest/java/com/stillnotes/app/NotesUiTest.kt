package com.stillnotes.app

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.PlatformTextInputMethodTestOverride
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * UI and real-device-storage regressions running on an Android emulator.
 *
 * Each test launches a fresh Activity with empty local files. The CI job runs this
 * same suite with both Android light and dark system appearances.
 */
@RunWith(AndroidJUnit4::class)
class NotesUiTest {
    private lateinit var scenario: ActivityScenario<MainActivity>

    // Compose captures failures before the outer rule closes the Activity.
    @get:Rule(order = 0)
    val activityCleanup = object : ExternalResource() {
        override fun after() {
            if (::scenario.isInitialized) scenario.close()
        }
    }

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Before
    fun startClean() {
        val directory = targetContext().filesDir
        check(File(directory, "stillnote-notes.json").let { !it.exists() || it.delete() })
        check(File(directory, "stillnote-notes.json.tmp").let { !it.exists() || it.delete() })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
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

    /**
     * Headless emulators can reject a text field's initial IME show request.
     * Re-tapping an already focused field is not a fresh request. After a
     * timeout, explicitly request the system keyboard again and require the
     * actual window IME inset to become visible before taking screenshots.
     * This retry is in test-only code, never in production UI.
     */
    private fun tapUntilKeyboardVisible(tag: String) {
        repeat(3) { attempt ->
            compose.onNodeWithTag(tag).performClick()
            compose.waitForIdle()
            try {
                compose.waitUntil(6_000) { isKeyboardVisible() }
                return
            } catch (error: ComposeTimeoutException) {
                if (attempt == 2) throw error
                scenario.onActivity { activity ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        activity.window.decorView.windowInsetsController
                            ?.show(WindowInsets.Type.ime())
                    } else {
                        val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                        activity.currentFocus?.let {
                            input.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            }
        }
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


    /**
     * Google Play uses REAL emulator captures with synthetic notes, not mocked
     * marketing UI. MediaStore keeps these captures across Gradle's teardown
     * of the instrumented APK and across the light/dark test phases.
     */
    private fun captureStoreListing(name: String) {
        val night = (targetContext().resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val filename = name + (if (night) "-dark.png" else "-light.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StillnoteQA")
        }
        val resolver = targetContext().contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use { stream ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun newDraft(title: String, body: String) {
        compose.onNodeWithContentDescription("새 메모").performClick()
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

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun koreanCompositionEmojiAndMultilineBodySurviveActivityRestart() {
        val title = "한글 제목 🌿"
        val rest = "글 본문입니다.\n두 번째 문단 😊\n마지막 줄"
        val body = compose.onNodeWithTag("note-body")
        var connection: InputConnection? = null

        // Use Compose's test session override to avoid racing the installed
        // keyboard with a second, manually created View input connection.
        // Exercise the real editor and storage, not a standalone text field.
        scenario.onActivity { activity ->
            activity.setContent {
                val hostView = LocalView.current
                val handler = remember(hostView) {
                    object : PlatformTextInputSession {
                        override val view = hostView
                        override suspend fun startInputMethod(
                            request: PlatformTextInputMethodRequest
                        ): Nothing {
                            connection = request.createInputConnection(EditorInfo())
                            try {
                                awaitCancellation()
                            } finally {
                                connection = null
                            }
                        }
                    }
                }
                StillnoteTheme {
                    PlatformTextInputMethodTestOverride(sessionHandler = handler) {
                        StillnoteApp(
                            model = viewModel(),
                            onCopy = {},
                            onShare = {},
                            onExportText = {},
                            onExportBackup = {},
                            onImportBackup = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("새 메모").performClick()
        compose.onNodeWithTag("note-title").performTextInput(title)
        body.performClick()
        compose.waitForIdle()
        val input = compose.runOnIdle { checkNotNull(connection) }

        // Check each complete composing replacement after recomposition.
        for (syllable in listOf("ㅎ", "하", "한")) {
            compose.runOnIdle {
                assertTrue("Text input session changed mid-composition", connection === input)
                assertTrue(input.setComposingText(syllable, 1))
            }
            body.assertTextContains(syllable, substring = false)
        }
        compose.runOnIdle {
            assertTrue(connection === input)
            assertTrue(input.finishComposingText())
            assertTrue(input.commitText(rest, 1))
        }
        body.assertTextContains("한" + rest, substring = false)

        saveDraft()
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        // Reopen the persisted note in the normal activity with the real IME.
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithTag("note-title").assertTextContains(title)
        body.assertTextContains("한" + rest, substring = false)
    }

    @Test
    fun bodyUndoAndRedoAreSavedAndDiscardDoesNotResurrectEdits() {
        saveNew("편집 이력 테스트", "원본 본문")
        compose.onNodeWithText("편집 이력 테스트").performClick()
        val body = compose.onNodeWithTag("note-body")

        body.performTextReplacement("복구할 문단")
        body.performTextReplacement("남은 문장")
        compose.onNodeWithContentDescription("본문 되돌리기").performClick()
        body.assertTextContains("복구할 문단")

        compose.onNodeWithContentDescription("본문 다시 실행").performClick()
        body.assertTextContains("남은 문장")

        compose.onNodeWithContentDescription("본문 되돌리기").performClick()
        saveDraft()
        compose.onNodeWithText("편집 이력 테스트").performClick()
        body.assertTextContains("복구할 문단")

        body.performTextReplacement("버릴 편집")
        compose.onNodeWithContentDescription("메모 고정").performClick()
        compose.onNodeWithText("버리기").performClick()
        body.assertTextContains("복구할 문단")
        compose.onNodeWithContentDescription("본문 되돌리기").assertIsNotEnabled()
        compose.onNodeWithContentDescription("본문 다시 실행").assertIsNotEnabled()
    }

    @Test
    fun titleLimitStillLetsUsersShortenAnExistingLongTitle() {
        val existingTitle = "가".repeat(122)
        scenario.close()
        File(targetContext().filesDir, "stillnote-notes.json").writeText(
            """[{"id":"legacy-title","title":"$existingTitle","body":"","createdAt":1,"updatedAt":1}]"""
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        // NotesStore restores the latest saved note directly into the editor.
        val title = compose.onNodeWithTag("note-title")
        title.assertTextContains(existingTitle)

        val shorterTitle = "가".repeat(121)
        title.performTextReplacement(shorterTitle)
        title.assertTextContains(shorterTitle)
        title.performTextReplacement("나".repeat(121))
        title.assertTextContains(shorterTitle)

        val allowedTitle = "가".repeat(120)
        title.performTextReplacement(allowedTitle)
        title.performTextReplacement("가".repeat(121))
        title.assertTextContains(allowedTitle)
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
        compose.onNodeWithContentDescription("목록").performClick()
        compose.onNodeWithText("변경 내용을 저장할까요?").assertIsDisplayed()
        compose.onNodeWithText("계속 편집").performClick()
        compose.onNodeWithTag("note-title").assertTextContains("확인창 테스트")

        compose.onNodeWithContentDescription("목록").performClick()
        compose.onNodeWithTag("unsaved-dialog-save").performClick()
        compose.onNodeWithText("확인창 테스트").assertExists()

        newDraft("버릴 초안", "임시 내용")
        compose.onNodeWithContentDescription("목록").performClick()
        compose.onNodeWithText("버리기").performClick()
        compose.onNodeWithText("확인창 테스트").assertExists()
        compose.onNodeWithText("버릴 초안").assertDoesNotExist()
    }

    @Test
    fun titleAndBodySearchFilterRealSavedNotes() {
        saveNew("첫 번째 메모", "여행 계획")
        saveNew("둘째 메모", "커피 기록")

        compose.onNodeWithText("검색").performClick()
        val search = compose.onNodeWithTag("notes-search").assertIsDisplayed()
        search.performTextInput("여행")
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
        compose.onNodeWithContentDescription("메모 고정").performClick()
        compose.onNodeWithContentDescription("목록").performClick()

        compose.onNodeWithText("고정된 메모").assertIsDisplayed()
        compose.onNodeWithText("최근 메모").assertIsDisplayed()
        compose.onNodeWithContentDescription("고정 메모").performClick()
        compose.onNodeWithText("고정할 메모").assertIsDisplayed()
        compose.onNodeWithText("일반 메모").assertDoesNotExist()
        compose.onNodeWithText("1개").assertIsDisplayed()
    }

    @Test
    fun deletionRequiresConfirmationAndUndoRestoresTheNote() {
        saveNew("삭제 확인 메모", "복원할 본문")
        compose.onNodeWithText("삭제 확인 메모").performClick()
        compose.onNodeWithContentDescription("메모 메뉴").performClick()
        compose.onNodeWithText("메모 삭제").performClick()
        compose.onNodeWithText("메모를 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithTag("delete-dialog-cancel").performClick()
        compose.onNodeWithTag("note-title").assertTextContains("삭제 확인 메모")

        compose.onNodeWithContentDescription("메모 메뉴").performClick()
        compose.onNodeWithText("메모 삭제").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("아직 메모가 없어요").assertIsDisplayed()
        compose.onNodeWithText("삭제 취소").performClick()
        compose.onNodeWithText("삭제 확인 메모").assertIsDisplayed()
    }


    @Test
    fun searchAndEditorRemainUsableWithTheKeyboardOpen() {
        compose.onNodeWithText("검색").performClick()
        // Regression for a collapsed UI with adjustResize and a visible IME.
        tapUntilKeyboardVisible("notes-search")
        compose.onNodeWithTag("notes-search").assertIsDisplayed()
        compose.onNodeWithTag("notes-search").performTextInput("검색 키보드")
        compose.onNodeWithTag("notes-search").assertIsDisplayed()
        captureKeyboardLayout("keyboard-list")

        compose.onNodeWithContentDescription("새 메모").performClick()
        tapUntilKeyboardVisible("note-title")
        compose.onNodeWithTag("note-title").assertIsDisplayed()
        compose.onNodeWithTag("note-title").performTextInput("키보드 레이아웃")
        compose.onNodeWithTag("note-title").assertIsDisplayed()

        compose.onNodeWithTag("note-body").performScrollTo()
        tapUntilKeyboardVisible("note-body")
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
    fun privacyPolicyIsReadableOfflineFromSettings() {
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("개인정보처리방침").performClick()
        compose.onNodeWithTag("privacy-policy-body").assertIsDisplayed()
        compose.onNodeWithTag("privacy-policy-body")
            .assertTextContains("Anna Johnny Works", substring = true)
        compose.onNodeWithTag("privacy-policy-body")
            .assertTextContains("dla3946gns@gmail.com", substring = true)

        compose.onNodeWithTag("privacy-policy-close").performClick()
        compose.onNodeWithTag("privacy-policy-body").assertDoesNotExist()
        compose.onNodeWithText("백업 가져오기").assertIsDisplayed()
        compose.onNodeWithText("홈").performClick()
        compose.onNodeWithText("아직 메모가 없어요").assertIsDisplayed()
    }

    @Test
    fun storeListingScreenshotsUseOnlySyntheticSampleNotes() {
        saveNew("오늘의 생각", "작은 생각도 포실포실 자라나요.")
        saveNew("이번 주 할 일", "책 읽기, 산책하기, 좋아하는 음악 듣기")
        // Screenshots must show a clean, stable real screen, not the temporary
        // "메모를 저장했어요" snackbar from the save that populated demo notes.
        compose.onNodeWithText("닫기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2개").assertIsDisplayed()
        captureStoreListing("store-list")
        compose.onNodeWithText("오늘의 생각").performClick()
        compose.onNodeWithTag("note-title").assertTextContains("오늘의 생각")
        compose.onNodeWithTag("note-body").assertTextContains("작은 생각도 포실포실 자라나요.")
        captureStoreListing("store-editor")
    }

    @Test
    fun appRendersCorrectBackgroundInCurrentLightOrDarkMode() {
        compose.onNodeWithText("AJ Notes").assertIsDisplayed()
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
