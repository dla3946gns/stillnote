package com.stillnote.shared

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
) {
    val displayTitle: String
        get() = title.trim().ifEmpty {
            body.trim().lineSequence().first().take(32).ifEmpty { "제목 없는 메모" }
        }

    val excerpt: String
        get() = body.trim().replace(Regex("\\s+"), " ").ifEmpty { "내용 없음" }

    val textContent: String
        get() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n\n")
}
