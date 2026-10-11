import SwiftUI
import UniformTypeIdentifiers

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json, .plainText] }
    var content: String

    init(content: String) { self.content = content }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let content = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadInapplicableStringEncoding)
        }
        self.content = content
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(content.utf8))
    }
}
