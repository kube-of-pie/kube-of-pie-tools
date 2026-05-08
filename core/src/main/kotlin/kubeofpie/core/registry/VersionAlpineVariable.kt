package kubeofpie.core.registry

import jakarta.inject.Singleton
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.storage.ConfigDatabase

@Singleton
class VersionAlpineVariable(
    private val database: ConfigDatabase,
    private val catalogue: AlpineCatalogue,
) : Variable {

    override val key: String = "version.alpine"
    override val description: String =
        "Alpine Linux release used to flash the SD cards."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = catalogue.supportedVersions()

    override fun read(): String? =
        database.connection().use { conn ->
            conn.prepareStatement("SELECT value FROM variables WHERE key = ?").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }

    override fun write(value: String) {
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
