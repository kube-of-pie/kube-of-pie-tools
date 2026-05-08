package kubeofpie.core.storage

import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DatabasePathTest {

    @Test
    fun `cli arg wins over env and default`() {
        val resolved = DatabasePath.resolve(
            cliArg = "/tmp/from-cli.db",
            env = mapOf(DatabasePath.ENV_VAR to "/tmp/from-env.db"),
        )
        assertEquals(Paths.get("/tmp/from-cli.db").toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun `env wins over default when cli arg absent`() {
        val resolved = DatabasePath.resolve(
            cliArg = null,
            env = mapOf(DatabasePath.ENV_VAR to "/tmp/from-env.db"),
        )
        assertEquals(Paths.get("/tmp/from-env.db").toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun `default is kube-of-pie dot db relative to cwd`() {
        val resolved = DatabasePath.resolve(cliArg = null, env = emptyMap())
        assertEquals(Paths.get(DatabasePath.DEFAULT_FILENAME).toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun `blank cli arg falls through to env`() {
        val resolved = DatabasePath.resolve(
            cliArg = "   ",
            env = mapOf(DatabasePath.ENV_VAR to "/tmp/from-env.db"),
        )
        assertEquals(Paths.get("/tmp/from-env.db").toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun `blank env value falls through to default`() {
        val resolved = DatabasePath.resolve(
            cliArg = null,
            env = mapOf(DatabasePath.ENV_VAR to ""),
        )
        assertEquals(Paths.get(DatabasePath.DEFAULT_FILENAME).toAbsolutePath().normalize(), resolved)
    }
}
