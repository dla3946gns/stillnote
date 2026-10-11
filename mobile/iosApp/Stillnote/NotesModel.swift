import Foundation
import Observation
import StillnoteShared

private final class LocalNotesStorage: NSObject, NotesStorage {
    private func fileURL() throws -> URL {
        let directory = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("Stillnote", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent("stillnote-notes.json")
    }

    func read() throws -> String? {
        let url = try fileURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try String(contentsOf: url, encoding: .utf8)
    }

    func write(content: String) throws {
        try content.write(to: fileURL(), atomically: true, encoding: .utf8)
    }
}

private final class StoreObserver: NSObject, NotesObserver {
    private let onChange: @MainActor (NotesState) -> Void

    init(onChange: @escaping @MainActor (NotesState) -> Void) {
        self.onChange = onChange
        super.init()
    }

    func onChanged(state: NotesState) {
        MainActor.assumeIsolated { onChange(state) }
    }
}

@MainActor
@Observable
final class NotesModel {
    private(set) var state: NotesState
    @ObservationIgnored private let store: NotesStore
    @ObservationIgnored private var observer: StoreObserver?

    init() {
        let store = NotesStore(storage: LocalNotesStorage())
        self.store = store
        state = store.state
        let observer = StoreObserver { [weak self] state in
            self?.state = state
        }
        self.observer = observer
        _ = store.addObserver(observer: observer)
    }

    var activeID: String? { state.activeNote?.id }
    var visibleNotes: [Note] { state.visibleNotes }

    func note(id: String) -> Note? {
        if let note = state.activeNote, note.id == id { return note }
        return state.notes.first { $0.id == id }
    }

    func select(_ id: String?) {
        if let id { store.selectNote(id: id) } else { store.closeNote() }
    }

    func create() { _ = store.createNote() }
    func save() -> Bool { store.saveNote() }
    func cancel() { store.cancelEdit() }
    func setQuery(_ query: String) { store.setQuery(query: query) }
    func setPinnedOnly(_ enabled: Bool) { store.setPinnedOnly(enabled: enabled) }
    func togglePin(id: String) { store.togglePinned(id: id) }
    func duplicate(id: String) { _ = store.duplicateNote(id: id) }
    func delete(id: String) { store.deleteNote(id: id) }
    func undoDelete() { store.undoDelete() }
    func retrySave() { store.retrySave() }
    func retryLoad() { store.retryLoad() }
    func clearMessage() { store.clearMessage() }
    func exportBackup() -> String { store.exportBackup() }
    func importBackup(_ content: String) { store.importBackup(content: content) }
    func text(id: String) -> String { store.textForNote(id: id) }

    func updateTitle(id: String, title: String) {
        guard let note = note(id: id) else { return }
        store.updateNote(id: id, title: title, body: note.body)
    }

    func updateBody(id: String, body: String) {
        guard let note = note(id: id) else { return }
        store.updateNote(id: id, title: note.title, body: body)
    }
}
