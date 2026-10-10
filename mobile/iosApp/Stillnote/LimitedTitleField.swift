import SwiftUI
import UIKit

/// UIKit exposes marked text so the title limit does not interrupt IME composition.
struct LimitedTitleField: UIViewRepresentable {
    @Binding var text: String

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.placeholder = "제목"
        field.font = UIFont.preferredFont(forTextStyle: .largeTitle).withTraits(.traitBold)
        field.adjustsFontForContentSizeCategory = true
        field.textColor = UIColor(Theme.ink)
        field.tintColor = UIColor(Theme.cocoa)
        field.returnKeyType = .done
        field.accessibilityLabel = "메모 제목"
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.editingChanged(_:)), for: .editingChanged)
        field.addTarget(context.coordinator, action: #selector(Coordinator.editingEnded(_:)), for: .editingDidEnd)
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        if field.markedTextRange == nil && field.text != text { field.text = text }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextField, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? uiView.intrinsicContentSize.width, height: uiView.intrinsicContentSize.height)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: LimitedTitleField
        private var pendingEdit = false

        init(parent: LimitedTitleField) { self.parent = parent }

        @objc func editingChanged(_ field: UITextField) {
            pendingEdit = true
            commitIfPossible(field)
        }

        @objc func editingEnded(_ field: UITextField) { commitIfPossible(field) }

        func textFieldDidChangeSelection(_ textField: UITextField) { commitIfPossible(textField) }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }

        private func commitIfPossible(_ field: UITextField) {
            guard pendingEdit, field.markedTextRange == nil else { return }
            let limited = String((field.text ?? "").prefix(120))
            if field.text != limited { field.text = limited }
            pendingEdit = false
            parent.text = limited
        }
    }
}

private extension UIFont {
    func withTraits(_ traits: UIFontDescriptor.SymbolicTraits) -> UIFont {
        guard let descriptor = fontDescriptor.withSymbolicTraits(traits) else { return self }
        return UIFont(descriptor: descriptor, size: pointSize)
    }
}
