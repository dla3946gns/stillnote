package com.stillnote.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android host/JVM regression tests of the shared notes domain model.
 * Storage is intentionally an in-memory stand-in: these do not exercise Android file I/O or UI.
 */
class NotesStoreTest {
    private class MemoryStorage(var data: String? = null) : NotesStorage {
        var failNextWrite = false
        var failRead = false

        override fun read(): String? {
            if (failRead) error("read unavailable")
            return data
        }

        override fun write(content: String) {
            if (failNextWrite) {
                failNextWrite = false
                error("disk full")
            }
            data = content
        }
    }

    private fun NotesStore.saveNew(title: String, body: String): String {
        val id = assertNotNull(createNote())
        updateNote(id, title, body)
        assertTrue(saveNote())
        return id
    }

    @Test
    fun newNoteIsNotPersistedUntilExplicitSave() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = assertNotNull(store.createNote())

        store.updateNote(id, "초안", "저장 전 내용")
        assertTrue(store.state.isNewNote)
        assertTrue(store.state.hasUnsavedChanges)
        assertEquals("초안", store.state.activeNote?.title)
        assertTrue(store.state.notes.isEmpty())
        assertNull(storage.data)

        assertTrue(store.saveNote())
        assertFalse(store.state.hasUnsavedChanges)
        assertEquals(1, store.state.notes.size)
        assertEquals("초안", NotesStore(storage).state.notes.single().title)
    }

    @Test
    fun cancelExistingEditRestoresLastSavedVersion() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = store.saveNew("저장된 제목", "저장된 본문")
        val originalBytes = storage.data

        store.updateNote(id, "바뀐 제목", "바뀐 본문")
        assertTrue(store.state.hasUnsavedChanges)
        store.cancelEdit()

        assertFalse(store.state.hasUnsavedChanges)
        assertEquals("저장된 제목", store.state.activeNote?.title)
        assertEquals("저장된 본문", store.state.activeNote?.body)
        assertEquals(originalBytes, storage.data)
        assertEquals("저장된 제목", NotesStore(storage).state.notes.single().title)
    }

    @Test
    fun cancelNewNoteDiscardsDraftWithoutWriting() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = assertNotNull(store.createNote())
        store.updateNote(id, "임시 메모", "삭제할 내용")

        store.cancelEdit()

        assertNull(store.state.activeNote)
        assertFalse(store.state.hasUnsavedChanges)
        assertTrue(store.state.notes.isEmpty())
        assertNull(storage.data)
    }

    @Test
    fun searchMatchesTitleOrBodyAndRespectsPinnedFilter() {
        val store = NotesStore(MemoryStorage())
        val first = store.saveNew("회의", "프로젝트 논의")
        val second = store.saveNew("일기", "Coffee break")
        store.togglePinned(first)

        store.setQuery("COFFEE")
        assertEquals(listOf(second), store.state.visibleNotes.map { it.id })
        store.setQuery("프로젝트")
        assertEquals(listOf(first), store.state.visibleNotes.map { it.id })
        store.setQuery("")
        store.setPinnedOnly(true)
        assertEquals(listOf(first), store.state.visibleNotes.map { it.id })
        store.setPinnedOnly(false)
        assertEquals(2, store.state.visibleNotes.size)
        assertEquals(first, store.state.visibleNotes.first().id)
    }

    @Test
    fun deleteAndUndoSurviveStoreReloads() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val first = store.saveNew("첫 메모", "내용")
        val second = store.saveNew("둘째 메모", "내용")

        store.deleteNote(first)

        assertEquals(listOf(second), NotesStore(storage).state.notes.map { it.id })
        assertTrue(store.state.canUndoDelete)
        store.undoDelete()
        assertFalse(store.state.canUndoDelete)
        assertEquals(setOf(first, second), NotesStore(storage).state.notes.map { it.id }.toSet())
    }

    @Test
    fun duplicateCreatesIndependentPersistedNote() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val first = store.saveNew("원본", "본문")
        val duplicate = assertNotNull(store.duplicateNote(first))

        assertTrue(first != duplicate)
        assertEquals("본문", NotesStore(storage).state.notes.first { it.id == duplicate }.body)
        assertEquals(2, store.state.notes.size)
    }

    @Test
    fun backupRoundTripRestoresNotesWithoutOverwritingMatchingIds() {
        val source = NotesStore(MemoryStorage())
        val id = source.saveNew("백업할 제목", "백업할 본문")
        val backup = source.exportBackup()

        val destinationStorage = MemoryStorage()
        val destination = NotesStore(destinationStorage)
        destination.importBackup(backup)
        assertEquals("백업할 본문", NotesStore(destinationStorage).state.notes.single().body)

        // Importing the same backup twice must not duplicate or overwrite an existing ID.
        destination.selectNote(id)
        destination.updateNote(id, "복원 이후 수정", "바뀐 내용")
        assertTrue(destination.saveNote())
        destination.importBackup(backup)
        assertEquals(1, destination.state.notes.size)
        assertEquals("바뀐 내용", destination.state.notes.single().body)
    }

    @Test
    fun malformedBackupNeverOverwritesSavedNotes() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        store.saveNew("기존", "안전한 내용")
        val before = storage.data

        store.importBackup("[{\"id\":42,\"title\":\"bad\",\"body\":\"bad\",\"createdAt\":1,\"updatedAt\":2}]")

        assertEquals(before, storage.data)
        assertEquals("기존", store.state.notes.single().title)
        assertTrue(store.state.message?.contains("올바른") == true)
    }

    @Test
    fun failedExplicitSaveKeepsCommittedDataAndDraftForRetry() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = store.saveNew("원래 제목", "원래 내용")
        val before = storage.data

        store.updateNote(id, "수정된 제목", "수정된 내용")
        storage.failNextWrite = true
        assertFalse(store.saveNote())

        assertTrue(store.state.saveFailed)
        assertTrue(store.state.hasUnsavedChanges)
        assertEquals("수정된 제목", store.state.activeNote?.title)
        assertEquals("원래 제목", store.state.notes.single().title)
        assertEquals(before, storage.data)
        assertEquals("원래 내용", NotesStore(storage).state.notes.single().body)

        store.retrySave()
        assertFalse(store.state.saveFailed)
        assertFalse(store.state.hasUnsavedChanges)
        assertEquals("수정된 내용", NotesStore(storage).state.notes.single().body)
    }

    @Test
    fun failedSaveThenCancelRestoresCommittedValue() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = store.saveNew("기존", "안전한 메모")
        val before = storage.data
        store.updateNote(id, "수정 중", "미저장")
        storage.failNextWrite = true
        assertFalse(store.saveNote())

        store.cancelEdit()

        assertFalse(store.state.hasUnsavedChanges)
        assertEquals("기존", store.state.activeNote?.title)
        assertEquals(before, storage.data)
    }

    @Test
    fun failedDeleteDoesNotRemoveNoteOrOfferUndo() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = store.saveNew("보존할 메모", "본문")
        val before = storage.data
        storage.failNextWrite = true

        store.deleteNote(id)

        assertTrue(store.state.saveFailed)
        assertEquals(before, storage.data)
        assertEquals(listOf(id), store.state.notes.map { it.id })
        assertEquals(id, store.state.activeNote?.id)
        assertFalse(store.state.canUndoDelete)
    }

    @Test
    fun failedPinChangeDoesNotChangeInMemoryOrDisk() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        val id = store.saveNew("고정 테스트", "본문")
        val before = storage.data
        storage.failNextWrite = true

        store.togglePinned(id)

        assertTrue(store.state.saveFailed)
        assertEquals(before, storage.data)
        assertFalse(store.state.notes.single().pinned)
        assertFalse(store.state.activeNote?.pinned ?: true)
    }

    @Test
    fun failedBackupImportDoesNotChangeExistingNotes() {
        val storage = MemoryStorage()
        val store = NotesStore(storage)
        store.saveNew("기존", "본문")
        val before = storage.data

        val backupSource = NotesStore(MemoryStorage())
        backupSource.saveNew("가져올 메모", "신규")
        storage.failNextWrite = true
        store.importBackup(backupSource.exportBackup())

        assertTrue(store.state.saveFailed)
        assertEquals(before, storage.data)
        assertEquals(1, store.state.notes.size)
        assertEquals("기존", store.state.notes.single().title)
    }

    @Test
    fun readFailureBlocksWritesUntilStorageRecovers() {
        val storage = MemoryStorage()
        storage.failRead = true
        val store = NotesStore(storage)

        assertTrue(store.state.loadFailed)
        assertNull(store.createNote())
        assertNull(storage.data)
        storage.failRead = false
        store.retryLoad()
        assertFalse(store.state.loadFailed)
        assertNotNull(store.createNote())
    }
}
