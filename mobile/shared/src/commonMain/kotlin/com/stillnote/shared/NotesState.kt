package com.stillnote.shared

data class NotesState(
    val notes: List<Note>,
    val visibleNotes: List<Note>,
    val activeNote: Note?,
    val query: String,
    val pinnedOnly: Boolean,
    val saveFailed: Boolean,
    val loadFailed: Boolean,
    val message: String?,
    val canUndoDelete: Boolean,
)
