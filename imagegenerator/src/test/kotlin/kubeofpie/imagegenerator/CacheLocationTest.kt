package kubeofpie.imagegenerator

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CacheLocationTest {

    private val location = CacheLocation()

    @Test
    fun `--cache-dir override wins over environment`(@TempDir tmp: Path) {
        val target = tmp.resolve("custom")
        val env = mapOf(CacheLocation.ENV_VAR to tmp.resolve("env").toString())
        assertEquals(target.toAbsolutePath().normalize(), location.resolve(target.toString(), env))
    }

    @Test
    fun `env var wins over the appdirs default`(@TempDir tmp: Path) {
        val envPath = tmp.resolve("env-cache")
        val env = mapOf(CacheLocation.ENV_VAR to envPath.toString())
        assertEquals(envPath.toAbsolutePath().normalize(), location.resolve(null, env))
    }

    @Test
    fun `blank override falls through to env, blank env falls through to appdirs`(@TempDir tmp: Path) {
        val envPath = tmp.resolve("env-cache")
        val env = mapOf(CacheLocation.ENV_VAR to envPath.toString())
        assertEquals(envPath.toAbsolutePath().normalize(), location.resolve("   ", env))
    }

    @Test
    fun `falls back to a non-empty absolute path from appdirs when no overrides given`() {
        val resolved = location.resolve(null, emptyMap())
        assertTrue(resolved.isAbsolute, "expected an absolute path, got $resolved")
        assertTrue(
            resolved.toString().contains(CacheLocation.APP_NAME),
            "expected the appdirs path to mention the app name, got $resolved",
        )
    }
}
