package com.stillnote.shared

/** Explicit results avoid Swift's nullable-return + NSError protocol limitation. */
class IosNotesReadResult(val content: String?, val errorMessage: String?)

interface IosNotesFiles {
    fun read(): IosNotesReadResult

    /** Null means the atomic write succeeded; otherwise return the I/O error. */
    fun write(content: String): String?
}

/** Keep errors inside Kotlin so NotesStore's existing rollback/guard logic runs. */
class IosNotesStorage(private val files: IosNotesFiles) : NotesStorage {
    override fun read(): String? {
        val result = files.read()
        result.errorMessage?.let { throw IllegalStateException(it) }
        return result.content
    }

    override fun write(content: String) {
        files.write(content)?.let { throw IllegalStateException(it) }
    }
}
