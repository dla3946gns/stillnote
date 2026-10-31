package com.stillnotes.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stillnote.shared.AuthProblem

private enum class AuthPage { LOGIN, REGISTER, RESET }

@Composable
fun AuthScreen(onClose: () -> Unit, model: AuthViewModel = viewModel()) {
    var page by remember { mutableStateOf(AuthPage.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visiblePassword by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deletionPassword by remember { mutableStateOf("") }
    fun changePage(next: AuthPage) { page = next; password = ""; model.clearMessage() }
    fun close() { password = ""; deletionPassword = ""; onClose() }
    BackHandler { if (page != AuthPage.LOGIN && model.account == null) changePage(AuthPage.LOGIN) else close() }
    LaunchedEffect(model.account?.uid) { password = ""; deletionPassword = ""; deleting = false }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 16.dp).testTag("auth-screen")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("StillNote", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = ::close) { Text("닫기") }
            }
            AnimatedContent(targetState = (model.account != null) to page, label = "account-page") { (signedIn, currentPage) ->
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(28.dp))
                    Text("A QUIET SPACE FOR YOU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(if (signedIn) "나의 계정" else when (currentPage) {
                        AuthPage.LOGIN -> "다시 만나 반가워요\n로그인해 주세요"
                        AuthPage.REGISTER -> "나만의 계정을\n만들어 볼까요?"
                        AuthPage.RESET -> "비밀번호를\n다시 설정해요"
                    }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (signedIn) {
                        Text(model.account?.email.orEmpty(), modifier = Modifier.testTag("auth-account-email"))
                        Text(if (model.account?.emailVerified == true) "이메일 확인 완료" else "이메일 확인이 필요해요.", style = MaterialTheme.typography.bodyMedium)
                        if (model.account?.emailVerified != true) {
                            TextButton(onClick = model::sendVerification, enabled = !model.busy) { Text("확인 메일 다시 보내기") }
                            TextButton(onClick = model::refreshAccount, enabled = !model.busy) { Text("이메일 확인 상태 새로고침") }
                        }
                        Text("계정은 기기의 메모를 자동으로 업로드하거나 동기화하지 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = model::signOut, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
                        TextButton(onClick = { deletionPassword = ""; deleting = true }, enabled = !model.busy) {
                            Text("계정 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(if (currentPage == AuthPage.RESET) "가입한 이메일로 재설정 링크를 보내드려요." else "StillNote 계정의 이메일을 입력해 주세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(28.dp))
                        OutlinedTextField(value = email, onValueChange = { email = it; model.clearMessage() }, label = { Text("이메일") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !model.busy,
                            modifier = Modifier.fillMaxWidth().testTag("auth-email"))
                        if (currentPage != AuthPage.RESET) {
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = password, onValueChange = { password = it; model.clearMessage() }, label = { Text("비밀번호") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !model.busy,
                                visualTransformation = if (visiblePassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { TextButton(onClick = { visiblePassword = !visiblePassword }) { Text(if (visiblePassword) "숨기기" else "보기") } },
                                modifier = Modifier.fillMaxWidth().testTag("auth-password"))
                        }
                        if (currentPage == AuthPage.LOGIN) TextButton(onClick = { changePage(AuthPage.RESET) }, enabled = !model.busy) { Text("비밀번호를 잊었나요?") }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            when (currentPage) {
                                AuthPage.LOGIN -> model.signIn(email, password)
                                AuthPage.REGISTER -> model.register(email, password)
                                AuthPage.RESET -> model.resetPassword(email)
                            }
                            password = ""
                        }, enabled = !model.busy && email.isNotBlank() && (currentPage == AuthPage.RESET || password.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("auth-submit")) {
                            Text(if (model.busy) "확인 중…" else when (currentPage) { AuthPage.LOGIN -> "로그인"; AuthPage.REGISTER -> "계정 만들기"; AuthPage.RESET -> "재설정 링크 받기" })
                        }
                        TextButton(onClick = { changePage(if (currentPage == AuthPage.LOGIN) AuthPage.REGISTER else AuthPage.LOGIN) }, enabled = !model.busy) {
                            Text(if (currentPage == AuthPage.LOGIN) "처음 오셨나요? 회원가입" else "로그인으로 돌아가기")
                        }
                        TextButton(onClick = ::close) { Text("로그인 없이 계속 쓰기") }
                    }
                }
            }
            val feedback = model.problem?.let(::authProblemText) ?: model.message
            if (feedback != null) Text(feedback, color = if (model.problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("auth-feedback"))
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { if (!model.busy) { deleting = false; deletionPassword = "" } },
        title = { Text("계정을 삭제할까요?") },
        text = { Column {
            Text("현재 비밀번호로 본인을 확인한 뒤 계정을 삭제해요. 기기 메모와 별도 백업은 삭제하지 않아요.")
            OutlinedTextField(value = deletionPassword, onValueChange = { deletionPassword = it }, label = { Text("현재 비밀번호") }, singleLine = true,
                enabled = !model.busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            model.problem?.let { Text(authProblemText(it), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { model.deleteAccount(deletionPassword); deletionPassword = "" }, enabled = !model.busy && deletionPassword.isNotEmpty()) { Text("계정 삭제") } },
        dismissButton = { TextButton(onClick = { deleting = false; deletionPassword = ""; model.clearMessage() }, enabled = !model.busy) { Text("취소") } }
    )
}

private fun authProblemText(problem: AuthProblem): String = when (problem) {
    AuthProblem.INVALID_EMAIL -> "이메일 주소를 확인해 주세요."
    AuthProblem.MISSING_PASSWORD -> "비밀번호를 입력해 주세요."
    AuthProblem.INVALID_CREDENTIALS -> "이메일과 비밀번호를 다시 확인해 주세요."
    AuthProblem.NETWORK -> "연결을 확인하고 다시 시도해 주세요."
    AuthProblem.TOO_MANY_REQUESTS -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
    AuthProblem.WEAK_PASSWORD -> "계정의 비밀번호 보안 요건을 충족하지 못했어요. 더 강한 비밀번호를 사용해 주세요."
    AuthProblem.EMAIL_IN_USE -> "이메일을 확인하거나 기존 계정으로 로그인해 주세요."
    AuthProblem.RECENT_LOGIN_REQUIRED -> "다시 로그인한 뒤 시도해 주세요."
    AuthProblem.SIGNED_OUT, AuthProblem.SESSION_CHANGED -> "로그인 상태가 바뀌었어요. 다시 확인해 주세요."
    AuthProblem.UNAVAILABLE -> "처리하지 못했어요. 잠시 후 다시 시도해 주세요."
}
