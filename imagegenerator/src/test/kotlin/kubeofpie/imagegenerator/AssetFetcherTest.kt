package kubeofpie.imagegenerator

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AssetFetcherTest {

    private val fetcher = AssetFetcher()

    @Test
    fun `cache hit returns the existing file and does not refetch`(@TempDir tmp: Path) {
        val cache = tmp.resolve("cache")
        Files.createDirectories(cache)
        val cached = cache.resolve("payload.bin")
        val bytes = byteArrayOf(0x10, 0x20, 0x30)
        Files.write(cached, bytes)

        // Even with a clearly bogus URL, the cache hit short-circuits the fetch.
        val result = fetcher.get("https://invalid.invalid/payload.bin", cache, offline = false)

        assertEquals(cached.toAbsolutePath(), result.toAbsolutePath())
        assertArrayEquals(bytes, Files.readAllBytes(result))
    }

    @Test
    fun `fetches from a file URL when cache is empty`(@TempDir tmp: Path) {
        val source = tmp.resolve("source.bin")
        val bytes = byteArrayOf(0x42, 0x43, 0x44, 0x00, 0x7F)
        Files.write(source, bytes)
        val cache = tmp.resolve("cache")

        val result = fetcher.get(source.toUri().toString(), cache, offline = false)

        assertEquals(cache.resolve("source.bin").toAbsolutePath().normalize(), result.toAbsolutePath().normalize())
        assertArrayEquals(bytes, Files.readAllBytes(result))
    }

    @Test
    fun `offline mode raises OfflineCacheMissException when cache is empty`(@TempDir tmp: Path) {
        val cache = tmp.resolve("cache")
        val ex = assertThrows(OfflineCacheMissException::class.java) {
            fetcher.get("https://invalid.invalid/missing.tar.gz", cache, offline = true)
        }
        assertEquals("https://invalid.invalid/missing.tar.gz", ex.url)
    }

    @Test
    fun `offline mode is fine when the cache is warm`(@TempDir tmp: Path) {
        val cache = tmp.resolve("cache")
        Files.createDirectories(cache)
        val cached = cache.resolve("payload.bin")
        Files.write(cached, byteArrayOf(0x01))

        val result = fetcher.get("https://invalid.invalid/payload.bin", cache, offline = true)
        assertEquals(cached.toAbsolutePath(), result.toAbsolutePath())
    }
}
