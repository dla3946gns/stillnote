package com.stillnote.shared

interface NotesStorage {
    @Throws(Exception::class)
    fun read(): String?

    @Throws(Exception::class)
    fun write(content: String)
}

interface NotesObserver {
    fun onChanged(state: NotesState)
}
