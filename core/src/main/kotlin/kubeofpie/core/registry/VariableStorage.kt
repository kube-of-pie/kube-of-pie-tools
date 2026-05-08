package kubeofpie.core.registry

import jakarta.inject.Singleton
import kubeofpie.core.storage.ConfigDatabase

/**
 * Thin helper around the `variables` table used by every [Variable] backed by the
 * config database. Reads and writes go through here so the SQL lives in one place;
 * each [Variable] still owns its own metadata, allowed-values, and parsing.
 */
@Singleton
class VariableStorage(private val database: ConfigDatabase) {

    fun read(key: String): String? =
        database.connection().use { conn ->
            conn.prepareStatement("SELECT value FROM variables WHERE key = ?").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }

    fun write(key: String, value: String) {
        database.connection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO variables(key, value) VALUES (?, ?) " +
                    "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
            ).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        }
    }
}
