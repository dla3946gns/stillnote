package com.stillnotes.app

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read counters after the passive window, then prove same-UID traffic is observable. */
@RunWith(AndroidJUnit4::class)
class StartupNetworkAuditTest {
    @Test
    fun collectCountersAndCalibrate() {
        val uid = Process.myUid()
        val beforeTx = TrafficStats.getUidTxBytes(uid)
        val beforeRx = TrafficStats.getUidRxBytes(uid)
        assertTrue("UID traffic counters unavailable", beforeTx >= 0 && beforeRx >= 0)

        val connection = URL("https://www.gstatic.com/generate_204")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val status = try { connection.responseCode } finally { connection.disconnect() }
        SystemClock.sleep(1_000)
        val afterTx = TrafficStats.getUidTxBytes(uid)
        val afterRx = TrafficStats.getUidRxBytes(uid)
        val report = JSONObject()
            .put("uid", uid)
            .put("before_positive_control_tx_bytes", beforeTx)
            .put("before_positive_control_rx_bytes", beforeRx)
            .put("after_positive_control_tx_bytes", afterTx)
            .put("after_positive_control_rx_bytes", afterRx)
            .put("positive_control_http_status", status)
            .put("scope", "Fresh test emulator UID; includes instrumentation startup before reading counters")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "startup-network-audit.json").writeText(report.toString(2))
        assertEquals(204, status)
        assertTrue("Positive control TX not observed", afterTx > beforeTx)
        assertTrue("Positive control RX not observed", afterRx > beforeRx)
    }
}
