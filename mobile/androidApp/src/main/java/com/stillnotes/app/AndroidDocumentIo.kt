package com.stillnotes.app

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

/**
 * UTF-8 I/O for Android's Storage Access Framework document Uris.
 *
 * Keep the ContentResolver operations shared between the real file picker
 * callbacks and emulator integration tests. Import uses strict decoding so
 * a damaged backup cannot silently turn into different user data.
 */
internal object AndroidDocumentIo {
    fun writeUtf8(resolver: ContentResolver, uri: Uri, content: String) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Document provider did not return a writable stream")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    fun readUtf8(resolver: ContentResolver, uri: Uri): String {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("Document provider did not return a readable stream")
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return InputStreamReader(stream, decoder).use { it.readText() }
    }
}
