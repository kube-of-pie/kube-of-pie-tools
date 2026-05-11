package kubeofpie.core.storage

import java.nio.file.Path
import java.sql.Connection
import javax.sql.DataSource
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.SQLiteOpenMode

// TODO(graalvm): when the native-image build target lands, add reflection config for
// `org.sqlite.JDBC` and resource config for `/org/sqlite/native/...` so the bundled
// driver loads in a static binary.
internal fun buildSqliteDataSource(path: Path, mode: OpenMode): DataSource {
    val config = SQLiteConfig().apply {
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
        enforceForeignKeys(true)
        if (mode == OpenMode.READ_ONLY) {
            setReadOnly(true)
            setOpenMode(SQLiteOpenMode.READONLY)
        }
    }
    val raw = SQLiteDataSource(config).apply {
        url = "jdbc:sqlite:${path.toAbsolutePath()}"
    }
    return ReadOnlyTolerantDataSource(raw)
}

/**
 * Wraps a SQLite [DataSource] so that `Connection.setReadOnly(...)` is silently
 * ignored. SQLite forbids changing the read-only flag after a connection is
 * established (it's URL-time only via [SQLiteConfig.setReadOnly]); Micronaut Data
 * calls `setReadOnly(true)` whenever it dispatches a read query, which would throw
 * `SQLException`. Tolerating the call is safe because:
 *
 *  - When the database was opened READ_WRITE, the connection is fully writable and
 *    Micronaut Data's read-only hint is purely advisory at the driver layer.
 *  - When the database was opened READ_ONLY, the connection is already read-only at
 *    the SQLite layer (set via [SQLiteConfig]) and the JDBC-level flag is redundant.
 */
private class ReadOnlyTolerantDataSource(
    private val delegate: DataSource,
) : DataSource by delegate {
    override fun getConnection(): Connection =
        ReadOnlyTolerantConnection(delegate.connection)

    override fun getConnection(username: String?, password: String?): Connection =
        ReadOnlyTolerantConnection(delegate.getConnection(username, password))
}

private class ReadOnlyTolerantConnection(
    delegate: Connection,
) : Connection by delegate {
    override fun setReadOnly(readOnly: Boolean) {
        // SQLite refuses; ignore.
    }
}
