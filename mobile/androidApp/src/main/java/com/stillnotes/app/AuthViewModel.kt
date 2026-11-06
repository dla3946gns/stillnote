package com.stillnotes.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.stillnote.shared.AuthProblem

class AuthViewModel : ViewModel() {
    private val gateway = AndroidEmailAuth()
    var account by mutableStateOf(gateway.currentAccount)
        private set
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<AuthProblem?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private val stopObserving = gateway.observe { account = it }

    fun clearMessage() { problem = null; message = null }

    private fun run(success: String, action: ((AuthProblem?) -> Unit) -> Unit) {
        if (busy) return
        busy = true
        clearMessage()
        action { error ->
            busy = false
            problem = error
            account = gateway.currentAccount
            message = if (error == null) success else null
        }
    }

    fun signIn(email: String, password: String) =
        run("로그인했어요.") { gateway.signIn(email, password, it) }

    fun register(email: String, password: String, ageConfirmed: Boolean) {
        if (busy) return
        busy = true
        clearMessage()
        gateway.register(email, password, ageConfirmed) { error ->
            if (error != null) {
                busy = false
                problem = error
            } else {
                gateway.sendVerification { verificationError ->
                    busy = false
                    account = gateway.currentAccount
                    message = if (verificationError == null) "계정을 만들었어요. 이메일 확인 메일을 확인해 주세요."
                        else "계정은 만들었지만 확인 메일을 보내지 못했어요. 다시 보내기를 눌러 주세요."
                }
            }
        }
    }

    fun resetPassword(email: String) = run("등록된 주소라면 재설정 메일을 받을 수 있어요.") {
        gateway.resetPassword(email, it)
    }
    fun sendVerification() = run("이메일 확인 메일을 보냈어요.", gateway::sendVerification)
    fun refreshAccount() = run("계정 상태를 확인했어요.", gateway::refreshAccount)
    fun deleteAccount(password: String) = run("계정을 삭제했어요. 기기의 메모는 그대로예요.") {
        gateway.deleteAccount(password, it)
    }
    fun signOut() {
        if (busy) return
        gateway.signOut()
        account = null
        clearMessage()
        message = "로그아웃했어요."
    }

    override fun onCleared() { stopObserving() }
}
