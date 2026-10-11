package com.stillnote.shared

data class NotesState(
    val notes: List<Note>,
    val visibleNotes: List<Note>,
    val activeNote: Note?,
    val hasUnsavedChanges: Boolean,
    val isNewNote: Boolean,
    val query: String,
    val pinnedOnly: Boolean,
    val saveFailed: Boolean,
    val loadFailed: Boolean,
    val message: String?,
    val canUndoDelete: Boolean,
)
