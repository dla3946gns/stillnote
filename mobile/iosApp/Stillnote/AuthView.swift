import SwiftUI
import StillnoteShared

@MainActor
struct AuthView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = AuthModel()
    @State private var page = Page.login
    @State private var email = ""
    @State private var password = ""
    @State private var revealPassword = false
    @State private var deleting = false
    @State private var deletionPassword = ""

    private enum Page: Equatable { case login, register, reset }
    private var title: String {
        if model.account != nil { return "나의 계정" }
        switch page {
        case .login: return "다시 만나 반가워요\n로그인해 주세요"
        case .register: return "나만의 계정을\n만들어 볼까요?"
        case .reset: return "비밀번호를\n다시 설정해요"
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("A QUIET SPACE FOR YOU").font(.caption2).tracking(2).foregroundStyle(Theme.muted)
                    Text(title).font(.largeTitle.bold()).fixedSize(horizontal: false, vertical: true)
                    if let account = model.account {
                        Text(account.email ?? "")
                        Text(account.emailVerified ? "이메일 확인 완료" : "이메일 확인이 필요해요.")
                        if !account.emailVerified {
                            Button("확인 메일 다시 보내기", action: model.sendVerification)
                            Button("이메일 확인 상태 새로고침", action: model.refreshAccount)
                        }
                        Text("계정은 기기의 메모를 자동으로 업로드하거나 동기화하지 않아요.")
                            .font(.subheadline).foregroundStyle(Theme.muted)
                        Button("로그아웃", action: model.signOut).buttonStyle(.bordered)
                        Button("계정 삭제", role: .destructive) { deletionPassword = ""; deleting = true }
                    } else {
                        Text(page == .reset ? "가입한 이메일로 재설정 링크를 보내드려요." : "StillNote 계정의 이메일을 입력해 주세요.")
                            .font(.subheadline).foregroundStyle(Theme.muted)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("이메일").font(.caption).foregroundStyle(Theme.muted)
                            TextField("name@example.com", text: $email)
                                .keyboardType(.emailAddress).textContentType(.emailAddress)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                                .accessibilityIdentifier("auth-email")
                        }.padding(14).background(Theme.ivory, in: RoundedRectangle(cornerRadius: 14))
                        if page != .reset {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("비밀번호").font(.caption).foregroundStyle(Theme.muted)
                                HStack {
                                    Group {
                                        if revealPassword { TextField("비밀번호를 입력해 주세요", text: $password) }
                                        else { SecureField("비밀번호를 입력해 주세요", text: $password) }
                                    }.textContentType(page == .register ? .newPassword : .password)
                                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                                        .accessibilityIdentifier("auth-password")
                                    Button(revealPassword ? "숨기기" : "보기") { revealPassword.toggle() }.font(.caption)
                                }
                            }.padding(14).background(Theme.ivory, in: RoundedRectangle(cornerRadius: 14))
                        }
                        if page == .login { Button("비밀번호를 잊었나요?") { changePage(.reset) }.font(.subheadline) }
                        Button(action: submit) {
                            Text(model.busy ? "확인 중…" : page == .login ? "로그인" : page == .register ? "계정 만들기" : "재설정 링크 받기")
                                .frame(maxWidth: .infinity).frame(minHeight: 44)
                        }.buttonStyle(.borderedProminent)
                            .disabled(email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (page != .reset && password.isEmpty))
                            .accessibilityIdentifier("auth-submit")
                        Button(page == .login ? "처음 오셨나요? 회원가입" : "로그인으로 돌아가기") {
                            changePage(page == .login ? .register : .login)
                        }.frame(maxWidth: .infinity)
                        Button("로그인 없이 계속 쓰기") { password = ""; dismiss() }.frame(maxWidth: .infinity)
                    }
                    if let problem = model.problem {
                        Text(problemText(problem)).foregroundStyle(.red).accessibilityIdentifier("auth-feedback")
                    } else if let message = model.message {
                        Text(message).foregroundStyle(Theme.muted).accessibilityIdentifier("auth-feedback")
                    }
                    if model.busy { ProgressView().frame(maxWidth: .infinity) }
                }
                .padding(26).disabled(model.busy)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: page)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: model.account?.uid)
            }
            .background(Theme.cream).foregroundStyle(Theme.ink)
            .navigationTitle("StillNote").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("닫기") { password = ""; dismiss() } } }
            .alert("계정을 삭제할까요?", isPresented: $deleting) {
                SecureField("현재 비밀번호", text: $deletionPassword)
                Button("계정 삭제", role: .destructive) {
                    model.deleteAccount(password: deletionPassword)
                    deletionPassword = ""
                }.disabled(deletionPassword.isEmpty)
                Button("취소", role: .cancel) { deletionPassword = "" }
            } message: {
                Text("현재 비밀번호로 본인을 확인한 뒤 계정을 삭제해요. 기기 메모와 별도 백업은 삭제하지 않아요.")
            }
            .onDisappear { password = ""; deletionPassword = "" }
        }
    }

    private func changePage(_ next: Page) { page = next; password = ""; model.clearMessage() }
    private func submit() {
        switch page {
        case .login: model.signIn(email: email, password: password)
        case .register: model.register(email: email, password: password)
        case .reset: model.resetPassword(email: email)
        }
        password = ""
    }

    private func problemText(_ problem: AuthProblem) -> String {
        switch problem {
        case .invalidEmail: return "이메일 주소를 확인해 주세요."
        case .missingPassword: return "비밀번호를 입력해 주세요."
        case .invalidCredentials: return "이메일과 비밀번호를 다시 확인해 주세요."
        case .network: return "연결을 확인하고 다시 시도해 주세요."
        case .tooManyRequests: return "요청이 많아요. 잠시 후 다시 시도해 주세요."
        case .weakPassword: return "계정의 비밀번호 보안 요건을 충족하지 못했어요. 더 강한 비밀번호를 사용해 주세요."
        case .emailInUse: return "이메일을 확인하거나 기존 계정으로 로그인해 주세요."
        case .recentLoginRequired: return "다시 로그인한 뒤 시도해 주세요."
        case .signedOut, .sessionChanged: return "로그인 상태가 바뀌었어요. 다시 확인해 주세요."
        default: return "처리하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
