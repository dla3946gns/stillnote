package com.stillnotes.app

import com.stillnote.shared.NotesStorage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class AndroidNotesStorage(directory: File) : NotesStorage {
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
