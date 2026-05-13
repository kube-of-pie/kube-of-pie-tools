package kubeofpie.imagegenerator

import io.micronaut.configuration.picocli.PicocliRunner
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kubeofpie.config.ConfigCommand
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ImageGeneratorCommandTest {

    @Test
    fun `generates a staging directory from a seeded configuration`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        seedFullConfig(dbPath)

        val tarball = tmp.resolve("alpine.tar.gz")
        buildAlpineFixture(tarball)
        val overlay = tmp.resolve("headless.apkovl.tar.gz")
        buildOverlayFixture(overlay)
        val outDir = tmp.resolve("out")
        val cacheDir = tmp.resolve("cache")

        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "master",
                "--out", outDir.toString(),
                "--db", dbPath,
                "--cache-dir", cacheDir.toString(),
                "--alpine-url", tarball.toUri().toString(),
                "--overlay-url", overlay.toUri().toString(),
            )
        }

        assertEquals(0, result.exitCode, "stderr=${result.err} stdout=${result.out}")
        assertTrue(result.out.contains("staged master"), result.out)
        assertEquals(
            "root=/dev/mmcblk0p2\n",
            Files.readString(outDir.resolve("boot/cmdline.txt"), StandardCharsets.UTF_8),
        )
        // Overlay copied byte-for-byte under its canonical name.
        assertEquals(
            Files.readAllBytes(overlay).toList(),
            Files.readAllBytes(outDir.resolve("headless.apkovl.tar.gz")).toList(),
        )

        val script = Files.readString(outDir.resolve("unattended.sh"), StandardCharsets.UTF_8)
        assertTrue(script.contains("HOSTNAME=\"rpi-master\""), script)
        assertTrue(script.contains("USERS=\"root\""), script)
        assertTrue(script.contains("USER_root_PASSWORD=\"hunter2\""), script)
        assertTrue(script.contains("USER_root_SSH_KEY=\"ssh-ed25519"), script)
        assertTrue(script.contains("ADDITIONAL_KERNEL_ARGS=\"cgroup_memory=1 cgroup_enable=memory\""), script)
    }

    @Test
    fun `fails cleanly when the database does not exist`(@TempDir tmp: Path) {
        val outDir = tmp.resolve("out")
        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "master",
                "--out", outDir.toString(),
                "--db", tmp.resolve("missing.db").toString(),
            )
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("no database at"), result.err)
    }

    @Test
    fun `fails cleanly when the node is not registered`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.21", "--db", dbPath)
        val outDir = tmp.resolve("out")

        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "ghost",
                "--out", outDir.toString(),
                "--db", dbPath,
            )
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("node not registered"), result.err)
    }

    @Test
    fun `fails cleanly when wifi is enabled without an SSID`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        seedMinimalConfig(dbPath)
        PicocliRunner.execute(
            ConfigCommand::class.java,
            "set", "nodes.master.network.wifi.enabled", "true", "--db", dbPath,
        )
        val outDir = tmp.resolve("out")

        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "master",
                "--out", outDir.toString(),
                "--db", dbPath,
            )
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("wifi is enabled"), result.err)
    }

    @Test
    fun `refuses to overwrite a non-empty output dir without --force`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        seedMinimalConfig(dbPath)
        val outDir = tmp.resolve("out")
        Files.createDirectories(outDir)
        Files.writeString(outDir.resolve("existing"), "x")

        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "master",
                "--out", outDir.toString(),
                "--db", dbPath,
            )
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("not empty"), result.err)
        // Pre-existing file untouched.
        assertTrue(Files.exists(outDir.resolve("existing")))
    }

    @Test
    fun `--offline with an empty cache fails before doing any work`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        seedMinimalConfig(dbPath)
        val outDir = tmp.resolve("out")
        val cacheDir = tmp.resolve("empty-cache")

        val result = capture {
            PicocliRunner.execute(
                ImageGeneratorCommand::class.java,
                "generate",
                "--node", "master",
                "--out", outDir.toString(),
                "--db", dbPath,
                "--cache-dir", cacheDir.toString(),
                "--offline",
            )
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("asset not in cache"), result.err)
        assertFalse(Files.exists(outDir.resolve("boot")))
    }

    private fun seedMinimalConfig(dbPath: String) {
        PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.21", "--db", dbPath)
        PicocliRunner.execute(ConfigCommand::class.java, "add", "nodes", "master", "--db", dbPath)
        PicocliRunner.execute(ConfigCommand::class.java, "set", "nodes.master.model", "pi4", "--db", dbPath)
    }

    private fun seedFullConfig(dbPath: String) {
        seedMinimalConfig(dbPath)
        PicocliRunner.execute(
            ConfigCommand::class.java,
            "set", "nodes.master.network.hostname", "rpi-master", "--db", dbPath,
        )
        PicocliRunner.execute(ConfigCommand::class.java, "add", "users", "root", "--db", dbPath)
        PicocliRunner.execute(
            ConfigCommand::class.java,
            "set", "users.root.password", "hunter2", "--db", dbPath,
        )
        PicocliRunner.execute(
            ConfigCommand::class.java,
            "set", "users.root.ssh.enabled", "true", "--db", dbPath,
        )
    }

    private fun buildAlpineFixture(target: Path) {
        Files.newOutputStream(target).use { fileOut ->
            BufferedOutputStream(fileOut).use { buf ->
                GzipCompressorOutputStream(buf).use { gz ->
                    TarArchiveOutputStream(gz).use { tar ->
                        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        putDir(tar, "boot/")
                        putFile(tar, "boot/cmdline.txt", "root=/dev/mmcblk0p2\n")
                        tar.finish()
                    }
                }
            }
        }
    }

    private fun buildOverlayFixture(target: Path) {
        Files.write(target, byteArrayOf(0x42, 0x43, 0x44, 0x45))
    }

    private fun putDir(tar: TarArchiveOutputStream, name: String) {
        val entry = TarArchiveEntry(name)
        entry.mode = 0b111_101_101
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun putFile(tar: TarArchiveOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = 0b110_100_100
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    private data class CapturedRun(val exitCode: Int, val out: String, val err: String)

    private fun capture(block: () -> Int): CapturedRun {
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val origOut = System.out
        val origErr = System.err
        System.setOut(PrintStream(outBuf, true))
        System.setErr(PrintStream(errBuf, true))
        return try {
            CapturedRun(block(), outBuf.toString(), errBuf.toString())
        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
        }
    }
}
