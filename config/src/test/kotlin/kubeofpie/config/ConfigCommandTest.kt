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

    @Test
    fun `add users appends names and list renders one identifier per line`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val firstAdd = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "add", "users", "root", "--db", dbPath)
        }
        assertEquals(0, firstAdd.exitCode)
        assertTrue(firstAdd.out.contains("added users.root"), firstAdd.out)

        val secondAdd = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "add", "users", "kubeofpie", "--db", dbPath)
        }
        assertEquals(0, secondAdd.exitCode)

        val list = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "list", "--prefix", "users", "--db", dbPath)
        }
        assertEquals(0, list.exitCode)
        assertTrue(list.out.contains("users:\n  root\n  kubeofpie"), list.out)
        assertTrue(list.out.contains("listable"), list.out)

        val get = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "get", "users", "--db", dbPath)
        }
        assertEquals(0, get.exitCode)
        assertEquals("root\nkubeofpie", get.out.trim())
    }

    @Test
    fun `remove users drops a known name`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        PicocliRunner.execute(ConfigCommand::class.java, "add", "users", "root", "--db", dbPath)
        PicocliRunner.execute(ConfigCommand::class.java, "add", "users", "kubeofpie", "--db", dbPath)

        val removed = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "remove", "users", "root", "--db", dbPath)
        }
        assertEquals(0, removed.exitCode)
        assertTrue(removed.out.contains("removed users.root"), removed.out)

        val get = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "get", "users", "--db", dbPath)
        }
        assertEquals("kubeofpie", get.out.trim())
    }

    @Test
    fun `add nodes auto-assigns sequential indices`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val first = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "add", "nodes", "--db", dbPath)
        }
        assertEquals(0, first.exitCode)
        assertTrue(first.out.contains("added nodes.0"), first.out)

        val second = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "add", "nodes", "--db", dbPath)
        }
        assertEquals(0, second.exitCode)
        assertTrue(second.out.contains("added nodes.1"), second.out)

        val get = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "get", "nodes", "--db", dbPath)
        }
        assertEquals("0\n1", get.out.trim())
    }

    @Test
    fun `remove nodes only accepts the highest index`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()
        PicocliRunner.execute(ConfigCommand::class.java, "add", "nodes", "--db", dbPath)
        PicocliRunner.execute(ConfigCommand::class.java, "add", "nodes", "--db", dbPath)

        val rejected = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "remove", "nodes", "0", "--db", dbPath)
        }
        assertEquals(2, rejected.exitCode)
        assertTrue(rejected.err.contains("highest index"), rejected.err)

        val accepted = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "remove", "nodes", "1", "--db", dbPath)
        }
        assertEquals(0, accepted.exitCode)
    }

    @Test
    fun `add rejects a non-listable variable`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val result = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "add", "version.alpine", "3.21", "--db", dbPath)
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("not a listable family"), result.err)
    }

    @Test
    fun `set rejects writes to the listable users head`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db").toString()

        val result = capture {
            PicocliRunner.execute(ConfigCommand::class.java, "set", "users", "[\"root\"]", "--db", dbPath)
        }
        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("not user-writable"), result.err)
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
