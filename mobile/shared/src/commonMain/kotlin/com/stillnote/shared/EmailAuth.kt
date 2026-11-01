package com.stillnote.shared

data class AuthAccount(val uid: String, val email: String?, val emailVerified: Boolean)

enum class AuthProblem {
    INVALID_EMAIL, MISSING_PASSWORD, INVALID_CREDENTIALS, NETWORK,
    TOO_MANY_REQUESTS, WEAK_PASSWORD, EMAIL_IN_USE, RECENT_LOGIN_REQUIRED,
    SIGNED_OUT, SESSION_CHANGED, UNAVAILABLE, AGE_CONFIRMATION_REQUIRED
}

/** Shared input rules; Firebase remains the authority for password policy and credentials. */
object EmailAuthInput {
    fun normalizedEmail(email: String): String = email.trim()

    fun validateRegistration(email: String, password: String, ageConfirmed: Boolean): AuthProblem? {
        if (!ageConfirmed) return AuthProblem.AGE_CONFIRMATION_REQUIRED
        return validate(email, password)
    }

    fun validate(email: String, password: String, passwordRequired: Boolean = true): AuthProblem? {
        val address = normalizedEmail(email)
        val separator = address.indexOf('@')
        if (separator <= 0 || separator != address.lastIndexOf('@') ||
            separator == address.lastIndex || address.any { it.isWhitespace() }) {
            return AuthProblem.INVALID_EMAIL
        }
        // Do not trim passwords or reject existing accounts using a new signup policy.
        if (passwordRequired && password.isEmpty()) return AuthProblem.MISSING_PASSWORD
        return null
    }
}
