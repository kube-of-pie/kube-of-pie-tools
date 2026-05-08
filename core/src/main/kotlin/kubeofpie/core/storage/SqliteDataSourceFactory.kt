package kubeofpie.core.storage

import java.nio.file.Path
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
    return SQLiteDataSource(config).apply {
        url = "jdbc:sqlite:${path.toAbsolutePath()}"
    }
}
