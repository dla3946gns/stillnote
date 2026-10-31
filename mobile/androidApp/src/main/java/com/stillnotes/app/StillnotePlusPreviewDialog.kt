package com.stillnotes.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Informational preview only. No BillingClient, purchase action, price or ad SDK
 * is available in this foundation PR. The menu entry is disabled by default.
 */
@Composable
internal fun StillnotePlusPreviewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stillnote Plus 미리보기") },
        text = {
            Column {
                Text("새로운 개인화 기능을 준비하고 있어요.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("• 광고 없는 메모 목록", Modifier.padding(top = 12.dp))
                Text("• 추가 색상 테마")
                Text("• 빠른 메모 템플릿")
                Text("현재는 구매할 수 없습니다. 무료 메모 기능은 그대로 유지돼요.",
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}
