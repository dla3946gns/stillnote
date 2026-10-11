package com.stillnotes.app

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stillnote.shared.NotesStore
import java.io.File
import java.nio.charset.CharacterCodingException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against a real Android ContentResolver and Android's Downloads provider.
 * This covers the same stream APIs used after the user selects a document in the
 * Storage Access Framework UI, without depending on localized picker labels.
 * The picker itself still needs a physical-device smoke test.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDocumentIoTest {
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var documentUri: Uri
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        resolver = context.contentResolver
        testDirectory = File(context.cacheDir, "backup-qa-${UUID.randomUUID()}").also {
            check(it.mkdirs())
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "stillnote-qa-${UUID.randomUUID()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/StillnoteQA/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        documentUri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "Downloads document provider is unavailable"
        }
    }

    @After
    fun tearDown() {
        if (::documentUri.isInitialized) resolver.delete(documentUri, null, null)
        if (::testDirectory.isInitialized) testDirectory.deleteRecursively()
    }

    private fun store(name: String): NotesStore {
        val directory = File(testDirectory, name)
        check(directory.mkdirs())
        return NotesStore(AndroidNotesStorage(directory))
    }

    private fun saveNote(store: NotesStore, title: String, body: String): String {
        val id = requireNotNull(store.createNote())
        store.updateNote(id, title, body)
        assertTrue(store.saveNote())
        return id
    }

    @Test
    fun backupDocumentRoundTripsUnicodeAndPinnedNotes() {
        val source = store("source")
        val id = saveNote(source, "한글 메모 🌿", "첫 줄\n두 번째 줄 😊")
        source.togglePinned(id)
        val sourceJson = source.exportBackup()

        AndroidDocumentIo.writeUtf8(resolver, documentUri, sourceJson)
        val savedJson = AndroidDocumentIo.readUtf8(resolver, documentUri)
        assertEquals(sourceJson, savedJson)

        val restored = store("restored")
        restored.importBackup(savedJson)
        assertEquals(1, restored.state.notes.size)
        assertEquals(id, restored.state.notes.single().id)
        assertEquals("한글 메모 🌿", restored.state.notes.single().title)
        assertEquals("첫 줄\n두 번째 줄 😊", restored.state.notes.single().body)
        assertTrue(restored.state.notes.single().pinned)

        // A second NotesStore instance reads what was really written to app storage.
        val reloaded = NotesStore(AndroidNotesStorage(File(testDirectory, "restored")))
        assertEquals(source.state.notes.single(), reloaded.state.notes.single())
    }

    @Test
    fun importingSameDocumentTwiceNeverOverwritesAnExistingNote() {
        val source = store("source")
        val id = saveNote(source, "가져올 제목", "백업 내용")
        AndroidDocumentIo.writeUtf8(resolver, documentUri, source.exportBackup())

        val restored = store("restored")
        restored.importBackup(AndroidDocumentIo.readUtf8(resolver, documentUri))
        restored.selectNote(id)
        restored.updateNote(id, "사용자가 고친 제목", "수정 이후 내용")
        assertTrue(restored.saveNote())
        val count = restored.state.notes.size
        restored.importBackup(AndroidDocumentIo.readUtf8(resolver, documentUri))
        assertEquals(count, restored.state.notes.size)
        assertEquals("사용자가 고친 제목", restored.state.notes.single().title)
        assertEquals("수정 이후 내용", restored.state.notes.single().body)
    }

    @Test
    fun damagedUtf8CannotBeUsedToImportOrOverwriteNotes() {
        val existing = store("existing")
        saveNote(existing, "보호할 메모", "손상되면 안 되는 내용")
        val before = existing.exportBackup()
        val damaged = byteArrayOf(0xC3.toByte(), 0x28)
        resolver.openOutputStream(documentUri, "wt")!!.use { it.write(damaged) }

        assertThrows(CharacterCodingException::class.java) {
            existing.importBackup(AndroidDocumentIo.readUtf8(resolver, documentUri))
        }
        assertEquals(before, existing.exportBackup())
        assertFalse(existing.state.saveFailed)
    }

    @Test
    fun invalidJsonDocumentDoesNotAlterExistingNotes() {
        val existing = store("existing")
        saveNote(existing, "기존 메모", "기존 본문")
        val before = existing.exportBackup()

        AndroidDocumentIo.writeUtf8(resolver, documentUri, "{not-valid-json")
        existing.importBackup(AndroidDocumentIo.readUtf8(resolver, documentUri))

        assertEquals(before, existing.exportBackup())
        assertTrue(existing.state.message?.contains("올바른") == true)
    }

    @Test
    fun providerMissingDocumentDoesNotDeleteCommittedLocalData() {
        val existing = store("existing")
        saveNote(existing, "안전한 메모", "안전한 본문")
        val before = existing.exportBackup()
        val missingUri = Uri.parse("content://com.stillnotes.missing-document-provider/never-exists")

        assertThrows(Exception::class.java) {
            AndroidDocumentIo.writeUtf8(resolver, missingUri, "replacement")
        }
        assertEquals(before, existing.exportBackup())
    }
}
