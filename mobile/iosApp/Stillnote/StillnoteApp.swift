import SwiftUI

@main
struct StillnoteApp: App {
    var body: some Scene {
        WindowGroup {
            NotesView()
                .tint(Theme.cocoa)
        }
    }
}
