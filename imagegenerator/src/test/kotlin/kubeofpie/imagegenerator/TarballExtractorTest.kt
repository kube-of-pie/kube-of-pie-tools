package kubeofpie.imagegenerator

import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TarballExtractorTest {

    private val extractor = TarballExtractor()

    @Test
    fun `extracts files and directories into the target dir`(@TempDir tmp: Path) {
        val tarball = tmp.resolve("input.tar.gz")
        buildTarball(tarball) { tar ->
            putDir(tar, "boot/")
            putFile(tar, "boot/cmdline.txt", "root=/dev/mmcblk0p2\n", mode = 0b110_100_100)
            putFile(tar, "etc/hostname", "rpi-master\n", mode = 0b110_100_100)
        }
        val out = tmp.resolve("out")

        extractor.extract(tarball, out)

        assertTrue(Files.isDirectory(out.resolve("boot")))
        assertEquals(
            "root=/dev/mmcblk0p2\n",
            Files.readString(out.resolve("boot/cmdline.txt"), StandardCharsets.UTF_8),
        )
        assertEquals(
            "rpi-master\n",
            Files.readString(out.resolve("etc/hostname"), StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `rejects entries that try to escape the target dir`(@TempDir tmp: Path) {
        val tarball = tmp.resolve("evil.tar.gz")
        buildTarball(tarball) { tar ->
            putFile(tar, "../evil.txt", "owned\n", mode = 0b110_100_100)
        }
        val out = tmp.resolve("out")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            extractor.extract(tarball, out)
        }
        assertTrue(ex.message?.contains("escaping target directory") == true, ex.message)
        assertFalse(Files.exists(tmp.resolve("evil.txt")))
    }

    @Test
    fun `binary file content round-trips byte-for-byte`(@TempDir tmp: Path) {
        val bytes = byteArrayOf(0x00, 0x7F, -0x80, 0x42, 0x00, 0x00, 0x10, 0x0A)
        val tarball = tmp.resolve("bin.tar.gz")
        buildTarball(tarball) { tar ->
            putFile(tar, "blob.bin", bytes, mode = 0b110_100_100)
        }
        val out = tmp.resolve("out")

        extractor.extract(tarball, out)

        assertArrayEquals(bytes, Files.readAllBytes(out.resolve("blob.bin")))
    }

    private fun buildTarball(target: Path, build: (TarArchiveOutputStream) -> Unit) {
        Files.newOutputStream(target).use { fileOut ->
            BufferedOutputStream(fileOut).use { buf ->
                GzipCompressorOutputStream(buf).use { gz ->
                    TarArchiveOutputStream(gz).use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        build(tar)
                        tar.finish()
                    }
                }
            }
        }
    }

    private fun putDir(tar: TarArchiveOutputStream, name: String) {
        val entry = TarArchiveEntry(name)
        entry.mode = 0b111_101_101
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun putFile(tar: TarArchiveOutputStream, name: String, content: String, mode: Int) {
        putFile(tar, name, content.toByteArray(StandardCharsets.UTF_8), mode)
    }

    private fun putFile(tar: TarArchiveOutputStream, name: String, bytes: ByteArray, mode: Int) {
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = mode
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }
}
