package com.stillnotes.app

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.stillnote.shared.AuthAccount
import com.stillnote.shared.AuthProblem
import com.stillnote.shared.EmailAuthInput

/** Thin SDK adapter. Construct only when the account feature is opened. No memo access. */
class AndroidEmailAuth(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    init { auth.useAppLanguage() }

    val currentAccount: AuthAccount?
        get() = auth.currentUser?.let { AuthAccount(it.uid, it.email, it.isEmailVerified) }

    fun observe(onChanged: (AuthAccount?) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChanged(currentAccount) }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }

    fun signIn(email: String, password: String, completed: (AuthProblem?) -> Unit) {
        EmailAuthInput.validate(email, password)?.let { completed(it); return }
        auth.signInWithEmailAndPassword(EmailAuthInput.normalizedEmail(email), password)
            .addOnCompleteListener { completed(if (it.isSuccessful) null else failure(it.exception)) }
    }

    fun register(email: String, password: String, completed: (AuthProblem?) -> Unit) {
        EmailAuthInput.validate(email, password)?.let { completed(it); return }
        auth.createUserWithEmailAndPassword(EmailAuthInput.normalizedEmail(email), password)
            .addOnCompleteListener { completed(if (it.isSuccessful) null else failure(it.exception)) }
    }

    fun resetPassword(email: String, completed: (AuthProblem?) -> Unit) {
        EmailAuthInput.validate(email, "", passwordRequired = false)?.let { completed(it); return }
        auth.sendPasswordResetEmail(EmailAuthInput.normalizedEmail(email)).addOnCompleteListener {
            val error = it.exception as? FirebaseAuthException
            // Keep reset responses neutral even when enumeration protection is not enabled.
            completed(if (it.isSuccessful || error?.errorCode == "ERROR_USER_NOT_FOUND") null else failure(it.exception))
        }
    }

    fun sendVerification(completed: (AuthProblem?) -> Unit) {
        val user = auth.currentUser ?: run { completed(AuthProblem.SIGNED_OUT); return }
        user.sendEmailVerification().addOnCompleteListener {
            completed(if (it.isSuccessful) null else failure(it.exception))
        }
    }

    fun refreshAccount(completed: (AuthProblem?) -> Unit) {
        val user = auth.currentUser ?: run { completed(AuthProblem.SIGNED_OUT); return }
        user.reload().addOnCompleteListener { completed(if (it.isSuccessful) null else failure(it.exception)) }
    }

    fun signOut() = auth.signOut()

    fun deleteAccount(password: String, completed: (AuthProblem?) -> Unit) {
        val user = auth.currentUser ?: run { completed(AuthProblem.SIGNED_OUT); return }
        val email = user.email ?: run { completed(AuthProblem.UNAVAILABLE); return }
        EmailAuthInput.validate(email, password)?.let { completed(it); return }
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).addOnCompleteListener { result ->
            if (!result.isSuccessful) {
                completed(failure(result.exception))
            } else if (auth.currentUser?.uid != user.uid) {
                completed(AuthProblem.SESSION_CHANGED)
            } else {
                user.delete().addOnCompleteListener { completed(if (it.isSuccessful) null else failure(it.exception)) }
            }
        }
    }

    private fun failure(error: Exception?): AuthProblem = when (error) {
        is FirebaseNetworkException -> AuthProblem.NETWORK
        is FirebaseTooManyRequestsException -> AuthProblem.TOO_MANY_REQUESTS
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> AuthProblem.INVALID_EMAIL
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" -> AuthProblem.INVALID_CREDENTIALS
            "ERROR_WEAK_PASSWORD" -> AuthProblem.WEAK_PASSWORD
            "ERROR_EMAIL_ALREADY_IN_USE" -> AuthProblem.EMAIL_IN_USE
            "ERROR_REQUIRES_RECENT_LOGIN" -> AuthProblem.RECENT_LOGIN_REQUIRED
            else -> AuthProblem.UNAVAILABLE
        }
        else -> AuthProblem.UNAVAILABLE
    }
}
