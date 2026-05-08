package kubeofpie.config

import io.micronaut.configuration.picocli.PicocliRunner
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigCommandTest {

    @Test
    fun `set creates the database, get reads it back, list shows it`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val first = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.21", "--db", dbPath)
        }
        assertEquals(0, first.exitCode)
        assertTrue(first.out.contains("created new database at"), first.out)

        val read = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "get", "version.alpine", "--db", dbPath)
        }
        assertEquals(0, read.exitCode)
        assertEquals("3.21", read.out.trim())

        val list = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "list", "--db", dbPath)
        }
        assertEquals(0, list.exitCode)
        assertTrue(list.out.contains("version.alpine = 3.21"), list.out)
        assertTrue(list.out.contains("[3.21]"), list.out)
    }

    @Test
    fun `set rejects values outside allowedValues with a clean error`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val result = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.99", "--db", dbPath)
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("not allowed"), result.err)
    }

    @Test
    fun `set on an existing database prints updated, not created`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.21", "--db", dbPath)

        val second = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "set", "version.alpine", "3.21", "--db", dbPath)
        }
        assertEquals(0, second.exitCode)
        assertTrue(second.out.contains("updated"), second.out)
    }

    @Test
    fun `get on a missing database fails with no database at`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("missing.db").toString()

        val result = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "get", "version.alpine", "--db", dbPath)
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("no database at"), result.err)
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
