package kubeofpie.imagegenerator

import jakarta.inject.Singleton
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Downloads remote assets (the Alpine `rpi` tarball and the headless bootstrap
 * overlay) into a local cache directory, returning the cached path. A cache hit
 * is a file with size > 0 at the expected location — Alpine's CDN URLs and the
 * pinned overlay tag URL are immutable, so we do not refresh on TTL.
 *
 * Uses JDK `HttpClient` (no extra dependency, also handles `file://` URLs that
 * tests use to point at local fixtures).
 *
 * Callers control offline behaviour via [offline]: when true, a cache miss is a
 * fatal error rather than a fetch attempt.
 */
@Singleton
class AssetFetcher {

    fun get(url: String, cacheDir: Path, offline: Boolean = false): Path {
        val target = cacheDir.resolve(basename(url))
        if (Files.exists(target) && Files.size(target) > 0) {
            return target
        }
        if (offline) {
            throw OfflineCacheMissException(url, target)
        }
        Files.createDirectories(cacheDir)
        val tmp = cacheDir.resolve("${target.fileName}.part")
        Files.deleteIfExists(tmp)
        try {
            val uri = URI.create(url)
            if (uri.scheme == "file") {
                Files.copy(java.nio.file.Paths.get(uri), tmp, StandardCopyOption.REPLACE_EXISTING)
            } else {
                val request = HttpRequest.newBuilder(uri).GET().build()
                val response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(tmp))
                if (response.statusCode() !in 200..299) {
                    Files.deleteIfExists(tmp)
                    throw DownloadException(url, "HTTP ${response.statusCode()}")
                }
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            Files.deleteIfExists(tmp)
            throw DownloadException(url, "I/O error: ${e.message}", e)
        }
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun basename(url: String): String {
        val path = URI.create(url).path ?: ""
        val last = path.substringAfterLast('/')
        require(last.isNotEmpty()) { "URL has no usable basename: $url" }
        return last
    }

    private companion object {
        val HTTP: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}

class OfflineCacheMissException(val url: String, val target: Path) :
    RuntimeException("asset not in cache: $target (url: $url)")

class DownloadException(val url: String, reason: String, cause: Throwable? = null) :
    RuntimeException("download failed: $url ($reason)", cause)
