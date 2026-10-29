package app.mizune.update

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** One file of a release, for one platform. */
class UpdateAsset(val name: String, val url: String, val size: Long, val sha256: String)

/** The latest release, as its signed update.json describes it; [notes] are its release notes, in Markdown. */
class UpdateRelease(val version: String, val page: String, val notes: String, val assets: Map<String, UpdateAsset>)

/**
 * The update feed. Every release carries an update.json and its signature (scripts/release.sh
 * writes both), and GitHub serves the latest release's copy at a fixed address. The signature is
 * checked against [KEYS] before anything in the file is trusted, and every download against the
 * SHA-256 the file gives for it, so only a release signed with our key can be installed.
 */
object Updates {
    const val FEED = "https://github.com/finchett/Mizune/releases/latest/download/update.json"
    const val RELEASES_PAGE = "https://github.com/finchett/Mizune/releases/latest"
    const val REPO_PAGE = "https://github.com/finchett/Mizune"
    const val ISSUES_PAGE = "$REPO_PAGE/issues"

    fun releasePage(version: String) = "$REPO_PAGE/releases/tag/v$version"

    /** Public halves of the release keys (P-256, X.509, base64). Add the new one here before retiring an old one. */
    private val KEYS = listOf(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDV2/PZWIw9VCxiBgeTVzPdTbN22KuwFFISJapNc93RTsYp88UeBeg5Pd6Nlm7MZ+ku95EXcUU6Baltuo0etYMQ==",
    )

    /**
     * [version]'s notes as its GitHub release gives them, or null if they can't be had. Not
     * signed like the feed, so only ever shown, never acted on.
     */
    fun releaseNotes(version: String): String? = runCatching {
        val release = JSONObject(String(fetch("https://api.github.com/repos/finchett/Mizune/releases/tags/v$version"), Charsets.UTF_8))
        release.optString("body").takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Fetches and verifies the latest release's update.json (and its .sig beside it). */
    fun latest(feed: String = FEED): UpdateRelease {
        val manifest = fetch(feed)
        val signature = fetch("$feed.sig")
        if (!verify(manifest, signature)) throw IOException("update.json isn't signed with a release key")
        return parse(String(manifest, Charsets.UTF_8))
    }

    fun verify(data: ByteArray, signature: ByteArray): Boolean = KEYS.any { key ->
        runCatching {
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        }.getOrDefault(false)
    }

    fun parse(json: String): UpdateRelease {
        val root = JSONObject(json)
        val assets = root.getJSONObject("assets")
        return UpdateRelease(
            version = root.getString("version"),
            page = root.optString("page", RELEASES_PAGE),
            notes = root.optString("notes", ""),
            assets = assets.keys().asSequence().associateWith { platform ->
                val asset = assets.getJSONObject(platform)
                UpdateAsset(asset.getString("name"), asset.getString("url"), asset.getLong("size"), asset.getString("sha256"))
            },
        )
    }

    /** True when [candidate] is a later version than [current]; both are dotted numbers like 0.5.0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun parts(version: String) = version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /**
     * Downloads [asset] to [target], through a .part file, and checks its size and SHA-256 before
     * it takes the final name. A file already there with the right hash is kept as it is.
     */
    fun download(asset: UpdateAsset, target: File, onProgress: (Float) -> Unit = {}): File {
        if (target.isFile && target.length() == asset.size && sha256(target) == asset.sha256) return target
        target.parentFile.mkdirs()
        val part = File(target.path + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        open(asset.url).let { connection ->
            try {
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            total += read
                            if (total > asset.size) throw IOException("${asset.name} is larger than update.json says")
                            onProgress(total.toFloat() / asset.size)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != asset.size || hash != asset.sha256) {
            part.delete()
            throw IOException("${asset.name} doesn't match update.json")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("couldn't move ${part.name} into place")
        return target
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetch(url: String): ByteArray {
        val connection = open(url)
        try {
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /** GitHub answers a release download with a redirect to its file store, which this follows. */
    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mizune-Updater")
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException("$url: HTTP $code")
        }
        return connection
    }
}
