package com.stillnotes.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.stillnote.shared.NotesObserver
import com.stillnote.shared.NotesState
import com.stillnote.shared.NotesStore

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
