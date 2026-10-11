package com.stillnotes.app

import com.stillnote.shared.NotesStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the real Android file adapter on the host JVM (no emulator required). */
class AndroidNotesStorageTest {
    private fun withTemporaryDirectory(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("stillnote-storage-test-").toFile()
        try {
            test(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun notePersistsAcrossIndependentStoreInstances() = withTemporaryDirectory { directory ->
        val first = NotesStore(AndroidNotesStorage(directory))
        val id = requireNotNull(first.createNote())
        first.updateNote(id, "한글 제목", "저장된 본문")
        assertTrue(first.saveNote())

        val second = NotesStore(AndroidNotesStorage(directory))
        assertEquals(1, second.state.notes.size)
        assertEquals("한글 제목", second.state.notes.single().title)
        assertEquals("저장된 본문", second.state.notes.single().body)
        assertFalse(File(directory, "stillnote-notes.json.tmp").exists())
    }

    @Test
    fun failedAtomicWritePreservesLastCommittedFile() = withTemporaryDirectory { directory ->
        val store = NotesStore(AndroidNotesStorage(directory))
        val id = requireNotNull(store.createNote())
        store.updateNote(id, "기존 메모", "기존 내용")
        assertTrue(store.saveNote())

        val destination = File(directory, "stillnote-notes.json")
        val before = destination.readBytes()
        // A directory where the staging file should be forces FileOutputStream to fail.
        val staged = File(directory, "stillnote-notes.json.tmp")
        assertTrue(staged.mkdir())
        store.updateNote(id, "덮어쓰기 시도", "실패할 내용")
        assertFalse(store.saveNote())

        assertTrue(store.state.saveFailed)
        assertTrue(store.state.hasUnsavedChanges)
        assertEquals("덮어쓰기 시도", store.state.activeNote?.title)
        assertArrayEquals(before, destination.readBytes())
        assertEquals("기존 메모", NotesStore(AndroidNotesStorage(directory)).state.notes.single().title)
    }

    @Test
    fun invalidUtf8FileIsNotSilentlyOverwritten() = withTemporaryDirectory { directory ->
        val file = File(directory, "stillnote-notes.json")
        val invalidBytes = byteArrayOf(0xC3.toByte(), 0x28)
        file.writeBytes(invalidBytes)

        val store = NotesStore(AndroidNotesStorage(directory))
        assertTrue(store.state.loadFailed)
        assertEquals(null, store.createNote())
        assertArrayEquals(invalidBytes, file.readBytes())
    }
}
