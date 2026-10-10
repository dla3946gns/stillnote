package com.stillnote.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.stillnote.shared.NotesObserver
import com.stillnote.shared.NotesState
import com.stillnote.shared.NotesStorage
import com.stillnote.shared.NotesStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    val store = NotesStore(AndroidNotesStorage(application.filesDir))
    var state by mutableStateOf(store.state)
        private set
    var platformMessage by mutableStateOf<String?>(null)
    var pendingDocument: String? = null
    var pendingImport by mutableStateOf<String?>(null)

    private val observerToken = store.addObserver(object : NotesObserver {
        override fun onChanged(state: NotesState) {
            this@NotesViewModel.state = state
        }
    })

    override fun onCleared() {
        store.removeObserver(observerToken)
    }
}

private class AndroidNotesStorage(directory: File) : NotesStorage {
    private val file = File(directory, "stillnote-notes.json")
    private val stagedFile = File(directory, "stillnote-notes.json.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return InputStreamReader(file.inputStream(), decoder).use { it.readText() }
    }

    override fun write(content: String) {
        try {
            FileOutputStream(stagedFile).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(stagedFile.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            stagedFile.delete()
        }
    }
}
