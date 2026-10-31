import Foundation
import Observation
import StillnoteShared

@MainActor
@Observable
final class AuthModel {
    private(set) var account: AuthAccount?
    private(set) var busy = false
    private(set) var problem: AuthProblem?
    private(set) var message: String?
    @ObservationIgnored private let gateway = AppleEmailAuth()

    init() { account = gateway.currentAccount }

    func clearMessage() { problem = nil; message = nil }

    private func run(success: String, action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        clearMessage()
        Task { @MainActor in
            defer { busy = false; account = gateway.currentAccount }
            do { try await action(); message = success }
            catch { problem = AppleEmailAuth.problem(for: error) }
        }
    }

    func signIn(email: String, password: String) {
        run(success: "로그인했어요.") { try await self.gateway.signIn(email: email, password: password) }
    }

    func register(email: String, password: String) {
        guard !busy else { return }
        busy = true
        clearMessage()
        Task { @MainActor in
            defer { busy = false; account = gateway.currentAccount }
            do {
                try await gateway.register(email: email, password: password)
                do {
                    try await gateway.sendVerification()
                    message = "계정을 만들었어요. 이메일 확인 메일을 확인해 주세요."
                } catch {
                    message = "계정은 만들었지만 확인 메일을 보내지 못했어요. 다시 보내기를 눌러 주세요."
                }
            } catch { problem = AppleEmailAuth.problem(for: error) }
        }
    }

    func resetPassword(email: String) {
        run(success: "등록된 주소라면 재설정 메일을 받을 수 있어요.") { try await self.gateway.resetPassword(email: email) }
    }
    func sendVerification() {
        run(success: "이메일 확인 메일을 보냈어요.") { try await self.gateway.sendVerification() }
    }
    func refreshAccount() {
        run(success: "계정 상태를 확인했어요.") { try await self.gateway.refreshAccount() }
    }
    func deleteAccount(password: String) {
        run(success: "계정을 삭제했어요. 기기의 메모는 그대로예요.") { try await self.gateway.deleteAccount(password: password) }
    }
    func signOut() {
        guard !busy else { return }
        clearMessage()
        do { try gateway.signOut(); account = nil; message = "로그아웃했어요." }
        catch { problem = AppleEmailAuth.problem(for: error) }
    }
}
