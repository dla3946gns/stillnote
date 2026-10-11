package com.stillnotes.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stillnote.shared.Note
import com.stillnote.shared.NotesState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StillnoteApp(
    model: NotesViewModel,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onExportText: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val state = model.state
    val store = model.store
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestAction(action: () -> Unit) {
        if (model.state.hasUnsavedChanges) pendingAction = action else {
            if (model.state.saveFailed) store.cancelEdit()
            action()
        }
    }
    fun finishPendingAction(save: Boolean) {
        if (save) {
            if (!store.saveNote()) return
        } else {
            store.cancelEdit()
        }
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            val message = model.platformMessage ?: state.message
            if (message != null) {
                Snackbar(
                    modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    dismissAction = {
                        TextButton(onClick = {
                            model.platformMessage = null
                            store.clearMessage()
                        }) { Text("닫기", color = MaterialTheme.colorScheme.inversePrimary) }
                    },
                    action = {
                        if (model.platformMessage == null && state.canUndoDelete) {
                            TextButton(onClick = { requestAction { store.undoDelete() } }, enabled = !state.saveFailed) {
                                Text("삭제 취소", color = MaterialTheme.colorScheme.inversePrimary)
                            }
                        } else {
                            TextButton(onClick = {
                                model.platformMessage = null
                                store.clearMessage()
                            }) { Text("확인", color = MaterialTheme.colorScheme.inversePrimary) }
                        }
                    }
                ) { Text(message) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.loadFailed) {
                StorageBanner("저장된 메모를 읽지 못했어요. 원본 파일은 보존됩니다.", "다시 읽기") {
                    store.retryLoad()
                }
            } else if (state.saveFailed) {
                StorageBanner("변경 내용을 저장하지 못했어요. 앱을 닫기 전에 다시 시도해 주세요.", "저장 재시도") {
                    store.retrySave()
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 840.dp
                BackHandler(enabled = state.hasUnsavedChanges || (!wide && state.activeNote != null)) {
                    requestAction { store.closeNote() }
                }
                Row(Modifier.fillMaxSize()) {
                    if (wide || state.activeNote == null) {
                        NotesList(
                            state = state,
                            modifier = if (wide) Modifier.width(320.dp) else Modifier.weight(1f),
                            onQuery = { store.setQuery(it) },
                            onPinnedOnly = { store.setPinnedOnly(it) },
                            onSelect = { id ->
                                if (id != state.activeNote?.id) requestAction { store.selectNote(id) }
                            },
                            onCreate = { requestAction { store.createNote() } },
                            onExportBackup = onExportBackup,
                            onImportBackup = { requestAction(onImportBackup) }
                        )
                    }
                    if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (wide || state.activeNote != null) {
                        Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                            val note = state.activeNote
                            if (note == null) {
                                EmptyEditor(canCreate = !state.loadFailed && !state.saveFailed) { store.createNote() }
                            } else {
                                key(note.id) {
                                    NoteEditor(
                                        note = note,
                                        state = state,
                                        showBack = !wide,
                                        onBack = { requestAction { store.closeNote() } },
                                        onEdit = { title, body -> store.updateNote(note.id, title, body) },
                                        onSave = { store.saveNote() },
                                        onCancel = { store.cancelEdit() },
                                        onPin = { requestAction { store.togglePinned(note.id) } },
                                        onDuplicate = { requestAction { store.duplicateNote(note.id) } },
                                        onDelete = { requestAction { deleteId = note.id } },
                                        onCopy = { onCopy(note.id) },
                                        onShare = { onShare(note.id) },
                                        onExportText = { onExportText(note.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("변경 내용을 저장할까요?") },
            text = {
                Text(if (state.saveFailed) "저장하지 못했어요. 다시 저장하거나 계속 편집해 주세요."
                    else "편집한 내용이 아직 저장되지 않았어요.")
            },
            confirmButton = {
                TextButton(onClick = { finishPendingAction(save = true) }, enabled = !state.loadFailed) {
                    Text("저장")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { finishPendingAction(save = false) }) { Text("버리기") }
                    TextButton(onClick = { pendingAction = null }) { Text("계속 편집") }
                }
            }
        )
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("메모를 삭제할까요?") },
            text = { Text("삭제 후 표시되는 ‘삭제 취소’로 복원할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let { store.deleteNote(it) }
                    deleteId = null
                }, enabled = !state.saveFailed && !state.loadFailed) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("취소") } }
        )
    }
    if (model.pendingImport != null) {
        AlertDialog(
            onDismissRequest = { model.pendingImport = null },
            title = { Text("백업 가져오기") },
            text = { Text("선택한 백업에서 메모를 가져올까요? 기존 메모는 유지합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val content = model.pendingImport
                    model.pendingImport = null
                    if (content != null) requestAction { store.importBackup(content) }
                }, enabled = !state.saveFailed && !state.loadFailed) { Text("가져오기") }
            },
            dismissButton = { TextButton(onClick = { model.pendingImport = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun StorageBanner(message: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun NotesList(
    state: NotesState,
    modifier: Modifier,
    onQuery: (String) -> Unit,
    onPinnedOnly: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Stillnote", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("더보기") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("전체 메모 백업") }, onClick = {
                        menuOpen = false
                        onExportBackup()
                    }, enabled = !state.loadFailed)
                    DropdownMenuItem(text = { Text("백업 가져오기") }, onClick = {
                        menuOpen = false
                        onImportBackup()
                    }, enabled = !state.loadFailed && !state.saveFailed)
                }
            }
        }
        Text("생각을 가볍게 남겨요", Modifier.padding(start = 20.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onCreate, enabled = !state.saveFailed && !state.loadFailed,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("새 메모") }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            label = { Text("메모 검색") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (state.query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("지우기") }
            }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = state.pinnedOnly, onClick = { onPinnedOnly(!state.pinnedOnly) },
                label = { Text("고정 메모") })
            Spacer(Modifier.weight(1f))
            Text("${state.visibleNotes.size}개", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.visibleNotes.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.query.isNotEmpty()) "검색 결과가 없어요" else if (state.pinnedOnly) "고정한 메모가 없어요" else "아직 메모가 없어요",
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(if (state.query.isNotEmpty()) "다른 검색어로 찾아보세요." else "새 메모로 첫 생각을 남겨보세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.visibleNotes, key = { it.id }) { note ->
                    Surface(
                        onClick = { onSelect(note.id) },
                        color = if (state.activeNote?.id == note.id) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().semantics { selected = state.activeNote?.id == note.id }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(note.displayTitle, Modifier.weight(1f), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                if (note.pinned) Text("고정", Modifier.padding(start = 8.dp),
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(note.excerpt, Modifier.padding(top = 6.dp), maxLines = 2,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Text(dateLabel(note.updatedAt), Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    note: Note,
    state: NotesState,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportText: () -> Unit
) {
    var title by rememberSaveable(note.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(note.title)) }
    var body by rememberSaveable(note.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(note.body)) }
    var menuOpen by remember(note.id) { mutableStateOf(false) }
    LaunchedEffect(note.title, note.body) {
        if (title.text != note.title) title = TextFieldValue(note.title)
        if (body.text != note.body) body = TextFieldValue(note.body)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (showBack) TextButton(onClick = onBack) { Text("목록") }
            val unsaved = state.hasUnsavedChanges || state.saveFailed
            Text(if (unsaved) "미저장" else "기기에 저장됨", Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (unsaved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("메모 메뉴") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val canChange = !state.isNewNote && !state.loadFailed && !state.saveFailed
                    fun closeAnd(action: () -> Unit) { menuOpen = false; action() }
                    DropdownMenuItem(text = { Text(if (note.pinned) "고정 해제" else "메모 고정") },
                        onClick = { closeAnd(onPin) }, enabled = canChange)
                    DropdownMenuItem(text = { Text("메모 복제") }, onClick = { closeAnd(onDuplicate) }, enabled = canChange)
                    DropdownMenuItem(text = { Text("내용 복사") }, onClick = { closeAnd(onCopy) })
                    DropdownMenuItem(text = { Text("공유") }, onClick = { closeAnd(onShare) })
                    DropdownMenuItem(text = { Text("텍스트 파일 저장") }, onClick = { closeAnd(onExportText) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("메모 삭제", color = MaterialTheme.colorScheme.error) },
                        onClick = { closeAnd(onDelete) }, enabled = canChange)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = state.hasUnsavedChanges || state.saveFailed) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = (state.hasUnsavedChanges || state.saveFailed) && !state.loadFailed) { Text("저장") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 20.dp)) {
            BasicTextField(
                value = title,
                onValueChange = { value ->
                    if (value.text == title.text || value.text.length <= 120 || value.text.length < title.text.length) {
                        title = value
                        onEdit(value.text, body.text)
                    }
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "메모 제목" },
                decorationBox = { field ->
                    Box {
                        if (title.text.isEmpty()) Text("제목", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            )
            Text("제목 ${title.text.length}/120자", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            BasicTextField(
                value = body,
                onValueChange = { value -> body = value; onEdit(title.text, value.text) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .semantics { contentDescription = "메모 내용" },
                decorationBox = { field ->
                    Box {
                        if (body.text.isEmpty()) Text("지금 떠오르는 생각을 적어보세요.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            )
            Text("본문 ${body.text.length}자 · ${dateLabel(note.updatedAt)}", Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyEditor(canCreate: Boolean, onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(160.dp).background(
                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface)), RoundedCornerShape(80.dp)
                ), contentAlignment = Alignment.Center
            ) { Text("Stillnote", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.height(24.dp))
            Text("작은 생각을 담는 공간", style = MaterialTheme.typography.headlineSmall)
            Text("메모를 선택하거나 새로 시작해 보세요.", Modifier.padding(top = 12.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onCreate, enabled = canCreate) { Text("새 메모") }
        }
    }
}

private fun dateLabel(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.KOREA).format(Date(timestamp))
