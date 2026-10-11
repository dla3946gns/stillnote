@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.stillnote.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Call the store and its observers on the platform's main/UI thread. */
class NotesStore(private val storage: NotesStorage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var notes = emptyList<Note>()
    private var activeId: String? = null
    private var draft: Note? = null
    private var query = ""
    private var pinnedOnly = false
    private var saveFailed = false
    private var saveFailureBeforeDraft: Boolean? = null
    private var loadFailed = false
    private var message: String? = null
    private var deletedNote: Note? = null
    private var deletedIndex = 0
    private var nextObserverToken = 0
    private val observers = linkedMapOf<Int, NotesObserver>()

    var state: NotesState = snapshot()
        private set

    init {
        load()
    }

    fun addObserver(observer: NotesObserver): Int {
        val token = nextObserverToken++
        observers[token] = observer
        observer.onChanged(state)
        return token
    }

    fun removeObserver(token: Int) {
        observers.remove(token)
    }

    fun createNote(): String? {
        if (!canWrite()) return null
        val now = Clock.System.now().toEpochMilliseconds()
        val note = Note(Uuid.random().toString(), "", "", now, now)
        activeId = note.id
        draft = note
        query = ""
        pinnedOnly = false
        message = null
        publish()
        return note.id
    }

    fun selectNote(id: String) {
        if (activeId == id) return
        val note = notes.find { it.id == id } ?: return
        activeId = id
        draft = note
        publish()
    }

    fun closeNote() {
        activeId = null
        draft = null
        publish()
    }

    fun updateNote(id: String, title: String, body: String) {
        if (!canWrite()) return
        val note = draft?.takeIf { it.id == id } ?: return
        if (note.title == title && note.body == body) return
        draft = note.copy(title = title, body = body)
        message = null
        publish()
    }

    fun saveNote(): Boolean {
        if (!canWrite()) return false
        val note = draft ?: return false
        if (!hasUnsavedChanges()) {
            if (!saveFailed) return true
            val saved = persist()
            if (saved) message = "변경 내용을 저장했어요."
            publish()
            return saved
        }
        val saved = note.copy(updatedAt = Clock.System.now().toEpochMilliseconds())
        val candidate = if (notes.any { it.id == saved.id }) {
            notes.map { if (it.id == saved.id) saved else it }
        } else {
            notes + saved
        }
        if (!persist(candidate, draftSave = true)) {
            publish()
            return false
        }
        notes = candidate
        draft = saved
        message = "메모를 저장했어요."
        publish()
        return true
    }

    fun cancelEdit() {
        draft = notes.find { it.id == activeId }
        if (draft == null) activeId = null
        saveFailureBeforeDraft?.let { saveFailed = it }
        saveFailureBeforeDraft = null
        if (!saveFailed) message = null
        publish()
    }

    fun setQuery(query: String) {
        this.query = query
        publish()
    }

    fun setPinnedOnly(enabled: Boolean) {
        pinnedOnly = enabled
        publish()
    }

    fun togglePinned(id: String) {
        if (!canWrite()) return
        val note = notes.find { it.id == id } ?: return
        val updated = note.copy(pinned = !note.pinned)
        notes = notes.map { if (it.id == id) updated else it }
        if (draft?.id == id) draft = draft?.copy(pinned = updated.pinned)
        if (persist()) message = if (updated.pinned) "메모를 고정했어요." else "고정을 해제했어요."
        publish()
    }

    fun duplicateNote(id: String): String? {
        if (!canWrite()) return null
        val note = notes.find { it.id == id } ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        val suffix = " (복사본)"
        val duplicate = Note(
            id = Uuid.random().toString(),
            title = note.displayTitle.take(120 - suffix.length) + suffix,
            body = note.body,
            createdAt = now,
            updatedAt = now,
        )
        val previousNotes = notes
        val previousActiveId = activeId
        notes = notes + duplicate
        activeId = duplicate.id
        if (!persist()) {
            notes = previousNotes
            activeId = previousActiveId
            publish()
            return null
        }
        if (pinnedOnly) {
            pinnedOnly = false
            query = ""
        }
        draft = duplicate
        message = "메모를 복제했어요."
        publish()
        return duplicate.id
    }

    fun deleteNote(id: String) {
        if (!canWrite()) return
        val index = notes.indexOfFirst { it.id == id }
        if (index < 0) return
        deletedNote = notes[index]
        deletedIndex = index
        notes = notes.filter { it.id != id }
        if (activeId == id) {
            draft = notes.maxByOrNull { it.updatedAt }
            activeId = draft?.id
        }
        if (persist()) message = "메모를 삭제했어요."
        publish()
    }

    fun undoDelete() {
        if (!canWrite()) return
        val note = deletedNote ?: return
        if (notes.any { it.id == note.id }) {
            deletedNote = null
            message = "이 메모는 이미 목록에 있어요."
            publish()
            return
        }
        val previousNotes = notes
        val previousActiveId = activeId
        notes = notes.toMutableList().apply { add(deletedIndex.coerceAtMost(size), note) }.toList()
        activeId = note.id
        if (!persist()) {
            notes = previousNotes
            activeId = previousActiveId
            publish()
            return
        }
        deletedNote = null
        draft = note
        message = "메모를 복원했어요."
        publish()
    }

    fun retrySave() {
        if (hasUnsavedChanges()) {
            saveNote()
            return
        }
        if (!canWrite()) return
        if (persist()) message = "변경 내용을 저장했어요."
        publish()
    }

    fun retryLoad() {
        if (loadFailed) load()
    }

    fun clearMessage() {
        message = null
        publish()
    }

    fun exportBackup(): String = json.encodeToString(notes)

    fun importBackup(content: String) {
        if (!canWrite()) return
        val imported = try {
            parseNotes(content)
        } catch (_: Exception) {
            message = "올바른 메모 백업 파일이 아니에요."
            publish()
            return
        }
        val ids = notes.mapTo(mutableSetOf()) { it.id }
        val additions = imported.filter { ids.add(it.id) }
        if (additions.isEmpty()) {
            message = "새 메모가 없어요. 기존 메모는 덮어쓰지 않았어요."
            publish()
            return
        }
        val previousNotes = notes
        notes = notes + additions
        if (!persist()) {
            notes = previousNotes
            publish()
            return
        }
        if (activeId == null) {
            draft = additions.maxByOrNull { it.updatedAt }
            activeId = draft?.id
        }
        message = "${additions.size}개 메모를 불러왔어요." +
            if (imported.size > additions.size) " 같은 ID의 메모는 건너뛰었어요." else ""
        publish()
    }

    fun textForNote(id: String): String =
        (draft?.takeIf { it.id == id } ?: notes.find { it.id == id })?.textContent.orEmpty()

    private fun load() {
        try {
            val loaded = storage.read()?.let(::parseNotes).orEmpty().distinctBy { it.id }
            notes = loaded
            activeId = loaded.maxByOrNull { it.updatedAt }?.id
            draft = loaded.find { it.id == activeId }
            saveFailed = false
            saveFailureBeforeDraft = null
            loadFailed = false
            message = null
        } catch (_: Exception) {
            loadFailed = true
            message = "저장된 메모를 읽을 수 없어요. 다시 불러오기를 시도해 주세요."
        }
        publish()
    }

    private fun canWrite(): Boolean {
        if (!loadFailed) return true
        message = "저장된 메모를 먼저 불러와 주세요."
        publish()
        return false
    }

    private fun persist(candidate: List<Note> = notes, draftSave: Boolean = false): Boolean = try {
        storage.write(json.encodeToString(candidate))
        saveFailed = false
        saveFailureBeforeDraft = null
        true
    } catch (_: Exception) {
        if (draftSave) {
            if (saveFailureBeforeDraft == null) saveFailureBeforeDraft = saveFailed
        } else if (saveFailureBeforeDraft != null) {
            saveFailureBeforeDraft = true
        }
        saveFailed = true
        message = "메모를 저장하지 못했어요. 저장을 다시 시도해 주세요."
        false
    }

    private fun parseNotes(content: String): List<Note> {
        val array = json.parseToJsonElement(content) as? JsonArray
            ?: throw IllegalArgumentException("Expected a note array")
        return array.map { element ->
            val item = element as? JsonObject ?: throw IllegalArgumentException("Expected a note object")
            for (key in listOf("id", "title", "body")) {
                require((item[key] as? JsonPrimitive)?.isString == true) { "Expected a string for $key" }
            }
            for (key in listOf("createdAt", "updatedAt")) {
                val value = item[key] as? JsonPrimitive
                require(value != null && !value.isString && value.longOrNull != null) { "Expected an epoch timestamp for $key" }
            }
            item["pinned"]?.let {
                require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "Expected a Boolean for pinned" }
            }
            json.decodeFromJsonElement<Note>(item)
        }
    }

    private fun snapshot(): NotesState {
        val foldedQuery = query.trim().lowercase()
        val visible = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            .filter { (!pinnedOnly || it.pinned) && (foldedQuery.isEmpty() || "${it.title}\n${it.body}".lowercase().contains(foldedQuery)) }
        return NotesState(
            notes = notes.toList(),
            visibleNotes = visible,
            activeNote = draft,
            hasUnsavedChanges = hasUnsavedChanges(),
            isNewNote = draft?.let { note -> notes.none { it.id == note.id } } == true,
            query = query,
            pinnedOnly = pinnedOnly,
            saveFailed = saveFailed,
            loadFailed = loadFailed,
            message = message,
            canUndoDelete = deletedNote != null,
        )
    }

    private fun hasUnsavedChanges(): Boolean =
        draft?.let { note -> note != notes.find { it.id == note.id } } == true

    private fun publish() {
        state = snapshot()
        observers.values.toList().forEach { it.onChanged(state) }
    }
}
