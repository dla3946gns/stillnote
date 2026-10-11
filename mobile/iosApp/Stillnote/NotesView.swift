import SwiftUI
import StillnoteShared
import UniformTypeIdentifiers
import UIKit

@MainActor
struct NotesView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var model = NotesModel()
    @State private var compactColumn: NavigationSplitViewColumn = .sidebar
    @State private var importing = false
    @State private var exporting = false
    @State private var backup = BackupDocument(content: "")
    @State private var backupFilename = "memo-backup"
    @State private var exportContentType: UTType = .json
    @State private var fileError: String?
    @State private var pendingAction: PendingAction?
    @State private var confirmingUnsavedChanges = false

    private enum PendingAction {
        case select(String?)
        case create
        case togglePin(String)
        case duplicate(String)
        case delete(String)
        case undoDelete
        case beginImport
        case importBackup(String)
    }

    var body: some View {
        NavigationSplitView(preferredCompactColumn: Binding(
            get: { compactColumn },
            set: { column in
                if column == .sidebar && horizontalSizeClass == .compact && model.activeID != nil {
                    request(.select(nil))
                } else {
                    compactColumn = column
                }
            }
        )) {
            sidebar
                .navigationTitle("Stillnote")
                .navigationSplitViewColumnWidth(min: 260, ideal: 310, max: 380)
        } detail: {
            if model.state.loadFailed {
                ContentUnavailableView {
                    Label("메모를 열지 못했어요", systemImage: "exclamationmark.folder")
                } description: {
                    Text("저장된 파일을 다시 읽어 보세요.")
                } actions: {
                    Button("다시 불러오기", action: model.retryLoad)
                        .buttonStyle(.borderedProminent)
                }
                .background(Theme.ivory)
            } else if let note = model.state.activeNote {
                NoteEditor(
                    model: model,
                    noteID: note.id,
                    exportText: exportText,
                    close: { request(.select(nil)) },
                    togglePin: { request(.togglePin(note.id)) },
                    duplicate: { request(.duplicate(note.id)) },
                    delete: { request(.delete(note.id)) }
                )
                    .id(note.id)
            } else {
                emptyEditor
            }
        }
        .foregroundStyle(Theme.ink)
        .safeAreaInset(edge: .bottom, spacing: 0) { statusPanel }
        .onChange(of: model.activeID) { _, id in
            compactColumn = id == nil ? .sidebar : .detail
        }
        .fileImporter(
            isPresented: $importing,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            do {
                guard let url = try result.get().first else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                request(.importBackup(try String(contentsOf: url, encoding: .utf8)))
            } catch { fileError = error.localizedDescription }
        }
        .fileExporter(
            isPresented: $exporting,
            document: backup,
            contentType: exportContentType,
            defaultFilename: backupFilename
        ) { result in
            if case .failure(let error) = result { fileError = error.localizedDescription }
        }
        .alert("파일을 처리하지 못했어요", isPresented: Binding(
            get: { fileError != nil },
            set: { if !$0 { fileError = nil } }
        )) {
            Button("확인", role: .cancel) { fileError = nil }
        } message: {
            Text(fileError ?? "")
        }
        .alert("변경 내용을 저장할까요?", isPresented: $confirmingUnsavedChanges) {
            Button("저장") {
                guard model.save() else {
                    pendingAction = nil
                    return
                }
                finishPendingAction()
            }
            Button("버리기", role: .destructive) {
                model.cancel()
                finishPendingAction()
            }
            Button("계속 편집", role: .cancel) { pendingAction = nil }
        } message: {
            Text("저장하지 않은 변경 내용이 있어요.")
        }
    }

    private func request(_ action: PendingAction) {
        if case .select(let id) = action, id == model.activeID {
            if id != nil { compactColumn = .detail }
            return
        }
        if model.state.hasUnsavedChanges {
            pendingAction = action
            confirmingUnsavedChanges = true
        } else {
            if model.state.saveFailed { model.cancel() }
            perform(action)
        }
    }

    private func finishPendingAction() {
        guard let action = pendingAction else { return }
        pendingAction = nil
        perform(action)
    }

    private func perform(_ action: PendingAction) {
        switch action {
        case .select(let id): model.select(id)
        case .create: model.create()
        case .togglePin(let id): model.togglePin(id: id)
        case .duplicate(let id): model.duplicate(id: id)
        case .delete(let id): model.delete(id: id)
        case .undoDelete: model.undoDelete()
        case .beginImport: importing = true
        case .importBackup(let content): model.importBackup(content)
        }
    }

    private var sidebar: some View {
        List {
            Section {
                ForEach(model.visibleNotes, id: \.id) { note in
                    Button {
                        request(.select(note.id))
                    } label: {
                        NoteRow(note: note)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(model.activeID == note.id ? Theme.apricot : Color.clear)
                    .swipeActions(edge: .leading, allowsFullSwipe: false) {
                        Button {
                            request(.togglePin(note.id))
                        } label: {
                            Label(note.pinned ? "고정 해제" : "고정", systemImage: note.pinned ? "pin.slash" : "pin")
                        }
                        .tint(Theme.cocoa)
                    }
                    .contextMenu {
                        Button(note.pinned ? "고정 해제" : "고정", systemImage: "pin") {
                            request(.togglePin(note.id))
                        }
                        Button("복제", systemImage: "plus.square.on.square") {
                            request(.duplicate(note.id))
                        }
                    }
                }
            } header: {
                HStack {
                    Text(model.state.pinnedOnly ? "고정한 메모" : "모든 메모")
                    Spacer()
                    Text("\(model.visibleNotes.count)")
                        .monospacedDigit()
                }
                .foregroundStyle(Theme.muted)
            } footer: {
                Label("메모는 이 기기에 저장돼요", systemImage: "lock")
                    .foregroundStyle(Theme.muted)
            }
        }
        .listStyle(.sidebar)
        .scrollContentBackground(.hidden)
        .background(Theme.cream)
        .overlay {
            if model.visibleNotes.isEmpty && !model.state.loadFailed {
                ContentUnavailableView {
                    Label(model.state.query.isEmpty ? "아직 메모가 없어요" : "검색 결과가 없어요", systemImage: "note.text")
                } description: {
                    Text(model.state.pinnedOnly ? "고정한 메모가 없어요. 고정 필터를 해제해 보세요." : "생각나는 순간 가볍게 적어두세요.")
                } actions: {
                    if !model.state.query.isEmpty {
                        Button("검색 지우기") { model.setQuery("") }
                    } else if model.state.pinnedOnly {
                        Button("모든 메모 보기") { model.setPinnedOnly(false) }
                    } else {
                        Button("첫 메모 쓰기") { request(.create) }
                    }
                }
            }
        }
        .searchable(text: Binding(get: { model.state.query }, set: model.setQuery), prompt: "메모 검색")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    model.setPinnedOnly(!model.state.pinnedOnly)
                } label: {
                    Label("고정한 메모만 보기", systemImage: model.state.pinnedOnly ? "pin.fill" : "pin")
                }
                .accessibilityValue(model.state.pinnedOnly ? "켜짐" : "꺼짐")
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Menu {
                    Button("백업 내보내기", systemImage: "square.and.arrow.up") {
                        backup = BackupDocument(content: model.exportBackup())
                        exportContentType = .json
                        let formatter = DateFormatter()
                        formatter.locale = Locale(identifier: "en_US_POSIX")
                        formatter.dateFormat = "yyyy-MM-dd"
                        backupFilename = "memo-backup-\(formatter.string(from: Date()))"
                        exporting = true
                    }
                    .disabled(model.state.loadFailed)
                    Button("백업 가져오기", systemImage: "square.and.arrow.down") {
                        request(.beginImport)
                    }
                    .disabled(model.state.loadFailed || model.state.saveFailed)
                } label: {
                    Label("백업", systemImage: "ellipsis")
                }
                Button("새 메모", systemImage: "square.and.pencil") { request(.create) }
                    .disabled(model.state.loadFailed)
            }
        }
    }

    private func exportText(id: String) {
        backup = BackupDocument(content: model.text(id: id))
        exportContentType = .plainText
        let title = model.note(id: id)?.displayTitle ?? "메모"
        backupFilename = String(title.prefix(80))
            .components(separatedBy: CharacterSet(charactersIn: "/\\:?*\"<>|\r\n\t"))
            .joined(separator: "_")
        exporting = true
    }

    private var emptyEditor: some View {
        ZStack {
            Theme.ivory
            RadialGradient(colors: [Theme.apricot.opacity(0.7), .clear], center: .center, startRadius: 10, endRadius: 260)
            ContentUnavailableView {
                Label("생각을 가볍게 담아두세요", systemImage: "note.text")
            } description: {
                Text("메모를 선택하거나 새 메모를 작성해 보세요.")
            } actions: {
                Button("새 메모", systemImage: "plus") { request(.create) }
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    @ViewBuilder
    private var statusPanel: some View {
        if model.state.loadFailed || model.state.saveFailed {
            HStack(spacing: 12) {
                Label(model.state.loadFailed ? "메모를 불러오지 못했어요" : "변경 내용을 저장하지 못했어요", systemImage: "exclamationmark.triangle")
                Spacer()
                Button("재시도") {
                    if model.state.loadFailed { model.retryLoad() } else { model.retrySave() }
                }
                .buttonStyle(.bordered)
            }
            .font(.footnote)
            .padding(12)
            .background(Theme.apricot)
        } else if model.state.canUndoDelete || model.state.message != nil {
            HStack(spacing: 12) {
                Text(model.state.message ?? "메모를 삭제했어요")
                    .lineLimit(2)
                Spacer()
                if model.state.canUndoDelete {
                    Button("실행 취소") { request(.undoDelete) }
                } else {
                    Button("닫기", systemImage: "xmark", action: model.clearMessage)
                        .labelStyle(.iconOnly)
                }
            }
            .font(.footnote)
            .padding(12)
            .background(Theme.cream)
        }
    }
}

private struct NoteRow: View {
    let note: Note

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(note.displayTitle).font(.headline).lineLimit(1)
                if note.pinned {
                    Image(systemName: "pin.fill")
                        .font(.caption)
                        .foregroundStyle(Theme.cocoa)
                        .accessibilityLabel("고정됨")
                }
            }
            Text(note.excerpt.isEmpty ? "내용 없음" : note.excerpt)
                .font(.subheadline)
                .foregroundStyle(Theme.muted)
                .lineLimit(2)
            Text(Date(timeIntervalSince1970: Double(note.updatedAt) / 1000), format: .dateTime.month().day().hour().minute())
                .font(.caption)
                .foregroundStyle(Theme.muted)
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .combine)
    }
}

@MainActor
private struct NoteEditor: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let model: NotesModel
    let noteID: String
    let exportText: (String) -> Void
    let close: () -> Void
    let togglePin: () -> Void
    let duplicate: () -> Void
    let delete: () -> Void
    @State private var deleting = false
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label(
                    model.state.saveFailed ? "저장 재시도 필요" : model.state.hasUnsavedChanges ? "저장하지 않은 변경" : "저장됨",
                    systemImage: model.state.saveFailed ? "exclamationmark.circle" : model.state.hasUnsavedChanges ? "pencil.circle" : "checkmark.circle"
                )
                Spacer()
                Text("\(model.note(id: noteID)?.body.count ?? 0)자")
                    .monospacedDigit()
            }
            .font(.caption)
            .foregroundStyle(Theme.muted)

            HStack(spacing: 12) {
                Button("취소", action: model.cancel)
                    .buttonStyle(.bordered)
                Button("저장") { _ = model.save() }
                    .buttonStyle(.borderedProminent)
            }
            .disabled(!model.state.hasUnsavedChanges && !model.state.saveFailed)

            LimitedTitleField(text: Binding(
                get: { model.note(id: noteID)?.title ?? "" },
                set: { model.updateTitle(id: noteID, title: $0) }
            ))
            .accessibilityLabel("메모 제목")

            Text("제목 \(model.note(id: noteID)?.title.count ?? 0)/120")
                .font(.caption)
                .foregroundStyle(Theme.muted)
                .monospacedDigit()

            Rectangle().fill(Theme.line).frame(height: 1)

            TextEditor(text: Binding(
                get: { model.note(id: noteID)?.body ?? "" },
                set: { model.updateBody(id: noteID, body: $0) }
            ))
            .font(.body)
            .scrollContentBackground(.hidden)
            .textInputAutocapitalization(.sentences)
            .accessibilityLabel("메모 내용")
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
        .background(Theme.ivory)
        .navigationTitle("메모")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbar {
            if horizontalSizeClass == .compact {
                ToolbarItem(placement: .topBarLeading) {
                    Button("목록", systemImage: "chevron.left", action: close)
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    togglePin()
                } label: {
                    Label(model.note(id: noteID)?.pinned == true ? "고정 해제" : "고정", systemImage: model.note(id: noteID)?.pinned == true ? "pin.fill" : "pin")
                }
                .disabled(model.state.isNewNote)
                Menu {
                    Button(copied ? "복사했어요" : "복사", systemImage: "doc.on.doc") {
                        UIPasteboard.general.string = model.text(id: noteID)
                        copied = true
                    }
                    ShareLink(item: model.text(id: noteID)) {
                        Label("공유", systemImage: "square.and.arrow.up")
                    }
                    Button("텍스트 파일 내보내기", systemImage: "doc.text") { exportText(noteID) }
                    Button("복제", systemImage: "plus.square.on.square", action: duplicate)
                        .disabled(model.state.isNewNote)
                    Divider()
                    Button("삭제", systemImage: "trash", role: .destructive) { deleting = true }
                        .disabled(model.state.isNewNote)
                } label: {
                    Label("메모 작업", systemImage: "ellipsis")
                }
            }
        }
        .confirmationDialog("메모를 삭제할까요?", isPresented: $deleting, titleVisibility: .visible) {
            Button("메모 삭제", role: .destructive, action: delete)
            Button("취소", role: .cancel) { }
        } message: {
            Text("삭제한 뒤 실행 취소로 복원할 수 있어요.")
        }
    }
}
