package com.stillnotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StillnoteTheme { AndroidNotesApp() } }
    }
}

@Composable
private fun AndroidNotesApp(model: NotesViewModel = viewModel()) {
    val context = LocalContext.current
    fun writeDocument(uri: Uri?) {
        val content = model.pendingDocument
        model.pendingDocument = null
        if (uri == null || content == null) return
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("No document stream")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            model.platformMessage = "파일을 저장했어요."
        } catch (_: Exception) {
            model.platformMessage = "파일을 저장하지 못했어요. 다시 시도해 주세요."
        }
    }
    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"), ::writeDocument
    )
    val textExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"), ::writeDocument
    )
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("No document stream")
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                model.pendingImport = InputStreamReader(stream, decoder).use { it.readText() }
            } catch (_: Exception) {
                model.platformMessage = "백업 파일을 읽지 못했어요. 다른 파일을 선택해 주세요."
            }
        }
    }
    StillnoteApp(
        model = model,
        onCopy = { id ->
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Stillnote 메모", model.store.textForNote(id)))
                model.platformMessage = "메모를 복사했어요."
            } catch (_: Exception) {
                model.platformMessage = "메모를 복사하지 못했어요. 파일 저장을 이용해 주세요."
            }
        },
        onShare = { id ->
            try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, model.store.textForNote(id))
                }, "메모 공유"))
            } catch (_: Exception) {
                model.platformMessage = "공유할 수 있는 앱을 찾지 못했어요."
            }
        },
        onExportText = { id ->
            model.pendingDocument = model.store.textForNote(id)
            textExport.launch("note-${fileTimestamp()}.txt")
        },
        onExportBackup = {
            model.pendingDocument = model.store.exportBackup()
            backupExport.launch("memo-backup-${fileTimestamp()}.json")
        },
        onImportBackup = { backupImport.launch(arrayOf("application/json", "text/json", "text/plain")) }
    )
}

private fun fileTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT).format(Date())
