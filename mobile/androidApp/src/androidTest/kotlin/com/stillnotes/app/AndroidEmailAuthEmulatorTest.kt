package com.stillnotes.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.stillnote.shared.AuthProblem
import com.stillnote.shared.NotesStore
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the local demo-project Auth emulator. Never creates production users or sends real mail. */
@RunWith(AndroidJUnit4::class)
class AndroidEmailAuthEmulatorTest {
    @Test
    fun accountLifecycleAndFailedReauthenticationPreserveNotes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-stillnote-auth")
            .setApplicationId("1:1234567890:android:0123456789abcdef")
            .setApiKey("fake-api-key-for-local-emulator")
            .build(), "isolated-auth-test")
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        val gateway = AndroidEmailAuth(auth)
        // Test storage is separate from actual app notes and is never an existing user directory.
        val notesDirectory = File(context.cacheDir, "auth-preservation-${UUID.randomUUID()}").apply { mkdirs() }
        val notes = NotesStore(AndroidNotesStorage(notesDirectory))
        val id = requireNotNull(notes.createNote())
        notes.updateNote(id, "Synthetic audit note", "Synthetic content only")
        assertTrue(notes.saveNote())
        val file = File(notesDirectory, "stillnote-notes.json")
        val original = file.readBytes()
        val email = "audit-${UUID.randomUUID()}@example.test"
        val password = "Synthetic-only-password-42!"
        try {
            assertNull(result { gateway.register(email, password, it) })
            assertEquals(email, gateway.currentAccount?.email)
            assertNull(result(gateway::sendVerification))
            gateway.signOut()
            assertNull(gateway.currentAccount)
            assertEquals(AuthProblem.INVALID_CREDENTIALS, result { gateway.signIn(email, "incorrect-test-value", it) })
            assertNull(result { gateway.resetPassword(email, it) })
            assertNull(result { gateway.resetPassword("absent-${UUID.randomUUID()}@example.test", it) })
            assertNull(result { gateway.signIn(email, password, it) })
            assertNull(result(gateway::refreshAccount))
            assertEquals(AuthProblem.INVALID_CREDENTIALS, result { gateway.deleteAccount("incorrect-test-value", it) })
            assertNotNull(gateway.currentAccount)
            assertNull(result { gateway.deleteAccount(password, it) })
            assertNull(gateway.currentAccount)
            assertArrayEquals(original, file.readBytes())
        } finally {
            auth.signOut()
            app.delete()
            notesDirectory.deleteRecursively()
        }
    }

    private fun result(action: ((AuthProblem?) -> Unit) -> Unit): AuthProblem? {
        val latch = CountDownLatch(1)
        var error: AuthProblem? = AuthProblem.UNAVAILABLE
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            action { error = it; latch.countDown() }
        }
        assertTrue("Auth emulator did not respond", latch.await(30, TimeUnit.SECONDS))
        return error
    }
}
