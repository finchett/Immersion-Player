package app.mizune.stats

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Anonymous usage counts, so we can see how many people use the app and on what. When the app
 * opens, and once a day while it stays open, it sends one event to Google Analytics' Measurement
 * Protocol carrying a random id made for this install, the platform, and the OS's and app's
 * versions. Nothing about what's watched, looked up or saved. Each app keeps the switch and the
 * id; this only sends, and does nothing until a measurement id and API secret are built in.
 */
class UsageStats(private val measurementId: String, private val apiSecret: String) {
    val configured get() = measurementId.isNotBlank() && apiSecret.isNotBlank()

    /** Sends the app-open event; false if it couldn't. Blocks, so call it off the UI thread. */
    fun appOpen(installId: String, platform: String, osVersion: String, appVersion: String): Boolean {
        if (!configured) return false
        val url = "https://www.google-analytics.com/mp/collect" +
            "?measurement_id=${encode(measurementId)}&api_secret=${encode(apiSecret)}"
        return runCatching {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json")
            try {
                connection.outputStream.use { it.write(payload(installId, platform, osVersion, appVersion).toByteArray()) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    companion object {
        const val EVENT = "app_open"

        /**
         * The request body. The engagement time and session make Analytics count the install as
         * an active user; the platform and versions go on the user too, to split the counts by.
         */
        fun payload(installId: String, platform: String, osVersion: String, appVersion: String, now: Long = System.currentTimeMillis()): String {
            val about = """"platform":${json(platform)},"os_version":${json(osVersion)},"app_version":${json(appVersion)}"""
            return """{"client_id":${json(installId)},""" +
                """"user_properties":{"platform":{"value":${json(platform)}},"app_version":{"value":${json(appVersion)}}},""" +
                """"events":[{"name":"$EVENT","params":{$about,"session_id":"${now / 1000}","engagement_time_msec":100}}]}"""
        }

        private fun json(text: String) = buildString {
            append('"')
            for (c in text) {
                when {
                    c == '"' || c == '\\' -> append('\\').append(c)
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

        private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)
    }
}
