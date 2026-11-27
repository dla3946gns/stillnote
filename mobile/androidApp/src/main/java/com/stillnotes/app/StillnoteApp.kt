package com.stillnotes.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val note = state.activeNote
    val editorFields = if (note != null) {
        key(note.id) {
            val title = rememberTextFieldState(note.title)
            val body = rememberTextFieldState(note.body)
            remember { NoteEditorFields(title, body) }
        }
    } else null

    fun syncDraft() {
        if (note != null && editorFields != null) {
            store.updateNote(note.id, editorFields.title.text.toString(), editorFields.body.text.toString())
        }
    }

    LaunchedEffect(note?.id, editorFields) {
        if (editorFields != null) {
            snapshotFlow { editorFields.title.text to editorFields.body.text }.collect { syncDraft() }
        }
    }

    fun discardDraft() {
        store.cancelEdit()
        val saved = store.state.activeNote
        if (saved != null && saved.id == note?.id && editorFields != null) {
            // A discarded edit can leave the editor open, for example when pinning a note.
            editorFields.title.setTextAndPlaceCursorAtEnd(saved.title)
            editorFields.body.setTextAndPlaceCursorAtEnd(saved.body)
            editorFields.title.undoState.clearHistory()
            editorFields.body.undoState.clearHistory()
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Avoid dismissing a newly focused search IME during the very first
    // composition. Only clear focus when the user actually switches editors
    // or returns to the note list; a startup effect can race with a first tap.
    var previousActiveNoteId by remember { mutableStateOf(state.activeNote?.id) }
    LaunchedEffect(state.activeNote?.id) {
        val currentId = state.activeNote?.id
        if (previousActiveNoteId != currentId) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        previousActiveNoteId = currentId
    }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestAction(action: () -> Unit) {
        syncDraft()
        if (model.state.hasUnsavedChanges) pendingAction = action else {
            if (model.state.saveFailed) discardDraft()
            action()
        }
    }
    fun finishPendingAction(save: Boolean) {
        if (save) {
            syncDraft()
            if (!store.saveNote()) return
        } else {
            discardDraft()
        }
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }
    var destination by rememberSaveable { mutableStateOf(NotesDestination.Home) }
    fun navigate(target: NotesDestination) {
        if (target == destination) return
        focusManager.clearFocus()
        keyboardController?.hide()
        if (target != NotesDestination.Search) store.setQuery("")
        destination = target
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
        // adjustResize already sizes the app's content above the IME. Applying
        // imePadding here as well on some devices collapses the weighted list/editor.
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loadFailed) {
                StorageBanner("저장된 메모를 읽지 못했어요. 원본 파일은 보존됩니다.", "다시 읽기") {
                    store.retryLoad()
                }
            } else if (state.saveFailed) {
                StorageBanner("변경 내용을 저장하지 못했어요. 앱을 닫기 전에 다시 시도해 주세요.", "저장 재시도") {
                    syncDraft()
                    store.retrySave()
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 840.dp
                BackHandler(enabled = state.hasUnsavedChanges || (!wide && state.activeNote != null) ||
                    destination != NotesDestination.Home) {
                    if (state.hasUnsavedChanges || (!wide && state.activeNote != null)) {
                        requestAction { store.closeNote() }
                    } else {
                        navigate(NotesDestination.Home)
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    if (wide || state.activeNote == null) {
                        NotesList(
                            state = state,
                            modifier = if (wide) Modifier.width(320.dp) else Modifier.weight(1f),
                            destination = destination,
                            onNavigate = ::navigate,
                            onQuery = { store.setQuery(it) },
                            onPinnedOnly = { store.setPinnedOnly(it) },
                            onSelect = { id ->
                                if (id != state.activeNote?.id) requestAction { store.selectNote(id) }
                            },
                            onCreate = { requestAction { store.createNote() } },
                            onExportBackup = onExportBackup,
                            onImportBackup = { requestAction(onImportBackup) },
                            onOpenPrivacyPolicy = { requestAction { showPrivacyPolicy = true } }
                        )
                    }
                    if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (wide || state.activeNote != null) {
                        Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                            if (note == null || editorFields == null) {
                                EmptyEditor(canCreate = !state.loadFailed && !state.saveFailed) { store.createNote() }
                            } else {
                                key(note.id) {
                                    NoteEditor(
                                        note = note,
                                        fields = editorFields,
                                        state = state,
                                        showBack = !wide,
                                        onBack = { requestAction { store.closeNote() } },
                                        onSave = {
                                            syncDraft()
                                            if (store.saveNote()) store.closeNote()
                                        },
                                        onCancel = {
                                            store.cancelEdit()
                                            store.closeNote()
                                        },
                                        onPin = { requestAction { store.togglePinned(note.id) } },
                                        onDuplicate = { requestAction { store.duplicateNote(note.id) } },
                                        onDelete = { requestAction { deleteId = note.id } },
                                        onCopy = {
                                            syncDraft()
                                            onCopy(note.id)
                                        },
                                        onShare = {
                                            syncDraft()
                                            onShare(note.id)
                                        },
                                        onExportText = {
                                            syncDraft()
                                            onExportText(note.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
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
                TextButton(onClick = { finishPendingAction(save = true) },
                    modifier = Modifier.testTag("unsaved-dialog-save"), enabled = !state.loadFailed) {
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
            dismissButton = {
                TextButton(onClick = { deleteId = null }, modifier = Modifier.testTag("delete-dialog-cancel")) {
                    Text("취소")
                }
            }
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
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Bundled content remains available offline even before public hosting is enabled.
    val policyText = remember(context) {
        context.assets.open("privacy-policy-ko.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개인정보처리방침") },
        text = {
            Text(
                text = policyText,
                modifier = Modifier.heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("privacy-policy-body"),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("privacy-policy-close")) {
                Text("닫기")
            }
        }
    )
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

private enum class NotesDestination(val label: String, val icon: Int) {
    Home("홈", R.drawable.ic_notes_home),
    Search("검색", R.drawable.ic_note_search),
    Settings("설정", R.drawable.ic_notes_settings)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NotesList(
    state: NotesState,
    modifier: Modifier,
    destination: NotesDestination,
    onNavigate: (NotesDestination) -> Unit,
    onQuery: (String) -> Unit,
    onPinnedOnly: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    val canCreate = !state.loadFailed && !state.saveFailed
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "notes destination"
        ) { page ->
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (page) {
                            NotesDestination.Home -> stringResource(R.string.app_name)
                            NotesDestination.Search -> "메모 찾기"
                            NotesDestination.Settings -> "앱 설정"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (page != NotesDestination.Settings) {
                        FilledTonalIconToggleButton(checked = state.pinnedOnly, onCheckedChange = onPinnedOnly) {
                            Icon(painterResource(R.drawable.ic_pin_note), contentDescription = "고정 메모")
                        }
                    }
                }
                if (page == NotesDestination.Settings) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                        Text("백업", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        TextButton(onClick = onExportBackup, enabled = !state.loadFailed) { Text("전체 메모 백업") }
                        TextButton(onClick = onImportBackup, enabled = canCreate) { Text("백업 가져오기") }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = onOpenPrivacyPolicy) { Text("개인정보처리방침") }
                    }
                } else {
                    if (page == NotesDestination.Search) NotesSearchField(state, onQuery)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        NotesFeed(state, grouped = page == NotesDestination.Home, onSelect = onSelect)
                        if (canCreate) FloatingActionButton(
                            onClick = onCreate,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(painterResource(R.drawable.ic_compose_note), contentDescription = "새 메모")
                        }
                    }
                }
            }
        }
        if (!WindowInsets.isImeVisible) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            NotesDestination.entries.forEach { page ->
                NavigationBarItem(
                    selected = destination == page,
                    onClick = { onNavigate(page) },
                    icon = { Icon(painterResource(page.icon), contentDescription = null) },
                    label = { Text(page.label) }
                )
            }
        }
    }
}

@Composable
private fun NotesFeed(state: NotesState, grouped: Boolean, onSelect: (String) -> Unit) {
    val notes = state.visibleNotes
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (notes.isEmpty()) {
            item {
                Text("0개", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.loadFailed) {
                item {
                    val message = when {
                        state.query.isNotEmpty() -> "검색 결과가 없어요"
                        state.pinnedOnly -> "고정한 메모가 없어요"
                        else -> "아직 메모가 없어요"
                    }
                    Text(message, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else if (grouped) {
            noteSection("고정된 메모", notes.filter { it.pinned }, state.activeNote?.id, onSelect)
            noteSection("최근 메모", notes.filterNot { it.pinned }, state.activeNote?.id, onSelect)
        } else {
            noteSection("검색 결과", notes, state.activeNote?.id, onSelect)
        }
    }
}

private fun LazyListScope.noteSection(
    title: String,
    notes: List<Note>,
    activeId: String?,
    onSelect: (String) -> Unit
) {
    if (notes.isEmpty()) return
    item(key = title) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${notes.size}개", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(notes, key = { it.id }) { note ->
        val isActive = activeId == note.id
        Surface(
            onClick = { onSelect(note.id) },
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().semantics { selected = isActive }
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

@Composable
private fun NotesSearchField(state: NotesState, onQuery: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = state.query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("notes-search"),
        placeholder = { Text("메모 검색") },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        leadingIcon = { Icon(painterResource(R.drawable.ic_note_search), contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        trailingIcon = {
            if (state.query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("지우기") }
        }
    )
}

private class NoteEditorFields(val title: TextFieldState, val body: TextFieldState)

private const val NOTE_TITLE_MAX_LENGTH = 120

private val noteTitleLengthLimit = InputTransformation {
    // Titles from an older app or a backup can already exceed the editor limit.
    if (length > NOTE_TITLE_MAX_LENGTH && length >= originalText.length &&
        !asCharSequence().contentEquals(originalText)
    ) {
        revertAllChanges()
    }
}

@Composable
private fun NoteEditor(
    note: Note,
    fields: NoteEditorFields,
    state: NotesState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportText: () -> Unit
) {
    val title = fields.title
    val body = fields.body
    val bodyHistory = body.undoState
    var menuOpen by remember(note.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        val canChange = !state.isNewNote && !state.loadFailed && !state.saveFailed
        val unsaved = state.hasUnsavedChanges || state.saveFailed
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (showBack) {
                FilledTonalIconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(painterResource(R.drawable.ic_note_back), contentDescription = "목록")
                }
            }
            Text("메모", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            NotesToolbar(Modifier.align(Alignment.CenterEnd)) {
                IconToggleButton(checked = note.pinned, onCheckedChange = { onPin() }, enabled = canChange) {
                    Icon(
                        painterResource(R.drawable.ic_pin_note),
                        contentDescription = if (note.pinned) "고정 해제" else "메모 고정",
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_notes_more), contentDescription = "메모 메뉴")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        fun closeAnd(action: () -> Unit) { menuOpen = false; action() }
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
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 12.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (unsaved) "미저장" else "저장됨", style = MaterialTheme.typography.labelMedium,
                color = if (unsaved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${body.text.length}자", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onCancel) { Text("취소") }
            Button(onClick = onSave, enabled = !state.loadFailed) { Text("저장") }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row {
                    IconButton(onClick = bodyHistory::undo, enabled = bodyHistory.canUndo) {
                        Icon(painterResource(R.drawable.ic_notes_undo), contentDescription = "본문 되돌리기")
                    }
                    IconButton(onClick = bodyHistory::redo, enabled = bodyHistory.canRedo) {
                        Icon(painterResource(R.drawable.ic_notes_redo), contentDescription = "본문 다시 실행")
                    }
                }
            }
        }
        // Allow the entire editor (including title) to scroll when the IME or
        // accessibility text size leaves less vertical space for composing.
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp).testTag("note-editor-scroll")) {
            BasicTextField(
                state = title,
                inputTransformation = noteTitleLengthLimit,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("note-title").semantics { contentDescription = "메모 제목" },
                decorator = { field ->
                    Box {
                        if (title.text.isEmpty()) Text("제목", style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            )
            Text("제목 ${title.text.length}/${NOTE_TITLE_MAX_LENGTH}자", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            BasicTextField(
                state = body,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                    .testTag("note-body").semantics { contentDescription = "메모 내용" },
                decorator = { field ->
                    Box {
                        if (body.text.isEmpty()) Text("지금 떠오르는 생각을 적어보세요.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                }
            )
        }
    }
}

@Composable
private fun NotesToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surface,
            toolbarContentColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(horizontal = 4.dp),
        expandedShadowElevation = 1.dp,
        content = content
    )
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
            ) { Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) }
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
