package com.example

import com.example.concordance.integration.aws.CloudWatchLogsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudWatchLogsManagerTest {

    @Test
    fun updateConfig_sets_log_group_and_stream() {
        CloudWatchLogsManager.updateConfig("my-group", "my-stream")
        assertEquals("my-group", CloudWatchLogsManager.logGroup.value)
        assertEquals("my-stream", CloudWatchLogsManager.logStream.value)
        // reset
        CloudWatchLogsManager.updateConfig("concordance-telemetry", "app-events")
    }

    @Test
    fun updateConfig_ignores_blank_values() {
        val beforeGroup = CloudWatchLogsManager.logGroup.value
        val beforeStream = CloudWatchLogsManager.logStream.value
        CloudWatchLogsManager.updateConfig("  ", null)
        assertEquals(beforeGroup, CloudWatchLogsManager.logGroup.value)
        assertEquals(beforeStream, CloudWatchLogsManager.logStream.value)
    }

    @Test
    fun putLogEvents_with_empty_list_returns_true_without_network() = runBlocking {
        val ok = CloudWatchLogsManager.putLogEvents("ak", "sk", "us-east-1", emptyList())
        assertTrue(ok)
    }
}
