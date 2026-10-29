package app.mizune.stats

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UsageStatsTest {
    @Test
    fun theEventCarriesOnlyTheInstallPlatformAndVersions() {
        val body = JSONObject(UsageStats.payload("a1b2", "windows", "10.0 \"24H2\"", "0.7.0", now = 1_700_000_000_000))
        assertEquals("a1b2", body.getString("client_id"))
        assertEquals("windows", body.getJSONObject("user_properties").getJSONObject("platform").getString("value"))
        val event = body.getJSONArray("events").getJSONObject(0)
        assertEquals(UsageStats.EVENT, event.getString("name"))
        val params = event.getJSONObject("params")
        assertEquals(setOf("platform", "os_version", "app_version", "session_id", "engagement_time_msec"), params.keySet())
        // quotes in an OS name don't break the JSON
        assertEquals("10.0 \"24H2\"", params.getString("os_version"))
        assertEquals("1700000000", params.getString("session_id"))
    }

    @Test
    fun withoutKeysNothingIsSent() {
        assertFalse(UsageStats("", "").appOpen("a1b2", "linux", "6.8", "0.7.0"))
    }
}
