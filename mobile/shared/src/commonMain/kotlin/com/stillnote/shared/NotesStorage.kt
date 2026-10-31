package com.stillnote.shared

/** Read canonical notes JSON; absent file returns "[]". I/O failures must throw.
 * A non-null return is required for Kotlin/Native's throwing Objective-C bridge. */
interface NotesStorage {
    @Throws(Exception::class)
    fun read(): String

    @Throws(Exception::class)
    fun write(content: String)
}

interface NotesObserver {
    fun onChanged(state: NotesState)
}
