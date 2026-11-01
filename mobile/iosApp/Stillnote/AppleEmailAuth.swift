import FirebaseAuth
import FirebaseCore
import Foundation
import StillnoteShared

/// Native SDK boundary; shared input rules live in KMP. No access to memo storage.
@MainActor
final class AppleEmailAuth {
    private let auth: Auth

    init() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        auth = Auth.auth()
        auth.useAppLanguage()
    }

    var currentAccount: AuthAccount? {
        guard let user = auth.currentUser else { return nil }
        return AuthAccount(uid: user.uid, email: user.email, emailVerified: user.isEmailVerified)
    }

    func signIn(email: String, password: String) async throws {
        try validate(email: email, password: password)
        _ = try await auth.signIn(withEmail: EmailAuthInput.shared.normalizedEmail(email: email), password: password)
    }

    func register(email: String, password: String, ageConfirmed: Bool) async throws {
        if let problem = EmailAuthInput.shared.validateRegistration(email: email, password: password, ageConfirmed: ageConfirmed) {
            throw localError(problem == .ageConfirmationRequired ? 5 : problem == .invalidEmail ? 1 : 2)
        }
        _ = try await auth.createUser(withEmail: EmailAuthInput.shared.normalizedEmail(email: email), password: password)
    }

    func resetPassword(email: String) async throws {
        try validate(email: email, password: "", passwordRequired: false)
        do { try await auth.sendPasswordReset(withEmail: EmailAuthInput.shared.normalizedEmail(email: email)) }
        catch {
            if AuthErrorCode(rawValue: (error as NSError).code) != .userNotFound { throw error }
        }
    }

    func sendVerification() async throws {
        guard let user = auth.currentUser else { throw localError(4) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.sendEmailVerification { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    func refreshAccount() async throws {
        guard let user = auth.currentUser else { throw localError(4) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.reload { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    func signOut() throws { try auth.signOut() }

    func deleteAccount(password: String) async throws {
        guard let user = auth.currentUser, let email = user.email else { throw localError(4) }
        try validate(email: email, password: password)
        let credential = EmailAuthProvider.credential(withEmail: email, password: password)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.reauthenticate(with: credential) { _, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
        guard auth.currentUser?.uid == user.uid else { throw localError(3) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.delete { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: ()) }
            }
        }
    }

    private func validate(email: String, password: String, passwordRequired: Bool = true) throws {
        if let problem = EmailAuthInput.shared.validate(email: email, password: password, passwordRequired: passwordRequired) {
            throw localError(problem == .invalidEmail ? 1 : 2)
        }
    }

    private func localError(_ code: Int) -> NSError { NSError(domain: "StillnoteAuthInput", code: code) }

    static func problem(for error: Error) -> AuthProblem {
        let value = error as NSError
        if value.domain == "StillnoteAuthInput" {
            switch value.code {
            case 1: return .invalidEmail
            case 2: return .missingPassword
            case 3: return .sessionChanged
            case 5: return .ageConfirmationRequired
            default: return .signedOut
            }
        }
        switch AuthErrorCode(rawValue: value.code) {
        case .invalidEmail: return .invalidEmail
        case .wrongPassword, .invalidCredential, .userNotFound, .userDisabled: return .invalidCredentials
        case .networkError: return .network
        case .tooManyRequests: return .tooManyRequests
        case .weakPassword: return .weakPassword
        case .emailAlreadyInUse: return .emailInUse
        case .requiresRecentLogin: return .recentLoginRequired
        default: return .unavailable
        }
    }
}
