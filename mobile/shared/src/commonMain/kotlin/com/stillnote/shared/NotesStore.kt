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
    private var query = ""
    private var pinnedOnly = false
    private var saveFailed = false
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
        notes = notes + note
        activeId = note.id
        query = ""
        pinnedOnly = false
        message = null
        persist()
        publish()
        return note.id
    }

    fun selectNote(id: String) {
        if (notes.none { it.id == id }) return
        activeId = id
        publish()
    }

    fun closeNote() {
        activeId = null
        publish()
    }

    fun updateNote(id: String, title: String, body: String) {
        if (!canWrite()) return
        val note = notes.find { it.id == id } ?: return
        if (note.title == title && note.body == body) return
        val updated = note.copy(title = title, body = body, updatedAt = Clock.System.now().toEpochMilliseconds())
        notes = notes.map { if (it.id == id) updated else it }
        message = null
        persist()
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
        if (activeId == id) activeId = notes.maxByOrNull { it.updatedAt }?.id
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
        message = "메모를 복원했어요."
        publish()
    }

    fun retrySave() {
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
        if (activeId == null) activeId = additions.maxByOrNull { it.updatedAt }?.id
        message = "${additions.size}개 메모를 불러왔어요." +
            if (imported.size > additions.size) " 같은 ID의 메모는 건너뛰었어요." else ""
        publish()
    }

    fun textForNote(id: String): String = notes.find { it.id == id }?.textContent.orEmpty()

    private fun load() {
        try {
            val loaded = storage.read()?.let(::parseNotes).orEmpty().distinctBy { it.id }
            notes = loaded
            activeId = loaded.maxByOrNull { it.updatedAt }?.id
            saveFailed = false
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

    private fun persist(): Boolean = try {
        storage.write(json.encodeToString(notes))
        saveFailed = false
        true
    } catch (_: Exception) {
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
            activeNote = notes.find { it.id == activeId },
            query = query,
            pinnedOnly = pinnedOnly,
            saveFailed = saveFailed,
            loadFailed = loadFailed,
            message = message,
            canUndoDelete = deletedNote != null,
        )
    }

    private fun publish() {
        state = snapshot()
        observers.values.toList().forEach { it.onChanged(state) }
    }
}
