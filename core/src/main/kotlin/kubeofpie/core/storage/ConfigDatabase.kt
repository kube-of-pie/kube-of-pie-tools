package kubeofpie.core.storage

import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import org.flywaydb.core.Flyway

/**
 * Holder for the cluster configuration database. The path is dynamic — known only
 * after the CLI parses its `--db` flag — so this bean exists in an *unopened* state
 * at context startup and the CLI calls [open] exactly once before any registry
 * read or write.
 *
 * **Open contract for [Variable] implementations:** call [connection] (or
 * [dataSource]) only from inside `read` / `write`, never from a constructor.
 * Constructors run when Micronaut builds the context, before [open] has been
 * called; `read` / `write` run after.
 */
@Singleton
class ConfigDatabase {

    private data class State(
        val path: Path,
        val mode: OpenMode,
        val dataSource: DataSource,
        val createdNew: Boolean,
    )

    private val state = AtomicReference<State?>(null)

    /**
     * Open the database at [path] in the given [mode]. In [OpenMode.READ_WRITE]
     * the file is created if missing and Flyway runs all pending migrations from
     * `classpath:db/migration`. In [OpenMode.READ_ONLY] a missing file raises
     * [NoDatabaseException]; migrations are never executed.
     *
     * Calling [open] twice with the same `(path, mode)` is a no-op that returns
     * the original [OpenResult]. Calling it with a different `(path, mode)` is
     * an error.
     */
    fun open(path: Path, mode: OpenMode): OpenResult {
        state.get()?.let { existing ->
            require(existing.path == path && existing.mode == mode) {
                "database already opened at ${existing.path} (${existing.mode}); refusing to reopen as $path ($mode)"
            }
            return OpenResult(existing.path, existing.createdNew, existing.dataSource)
        }

        val createdNew = !Files.exists(path)
        if (mode == OpenMode.READ_ONLY && createdNew) {
            throw NoDatabaseException(path)
        }

        if (createdNew) {
            Files.createDirectories(path.toAbsolutePath().parent)
        }

        val dataSource = buildSqliteDataSource(path, mode)

        if (mode == OpenMode.READ_WRITE) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        val newState = State(path, mode, dataSource, createdNew)
        if (!state.compareAndSet(null, newState)) {
            // Lost the race; discard our build and reuse the winner's state.
            val winner = state.get()!!
            require(winner.path == path && winner.mode == mode) {
                "database already opened at ${winner.path} (${winner.mode}); refusing to reopen as $path ($mode)"
            }
            return OpenResult(winner.path, winner.createdNew, winner.dataSource)
        }
        return OpenResult(path, createdNew, dataSource)
    }

    fun isOpen(): Boolean = state.get() != null

    fun dataSource(): DataSource =
        state.get()?.dataSource ?: error("database not opened")

    fun connection(): Connection = dataSource().connection
}

data class OpenResult(
    val path: Path,
    val createdNew: Boolean,
    val dataSource: DataSource,
)
