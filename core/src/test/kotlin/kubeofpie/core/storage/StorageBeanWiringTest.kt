package kubeofpie.core.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.qualifiers.Qualifiers
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Smoke test that the storage beans (`ConfigDatabase`, `DataSourceFactory`) are
 * discovered by KSP and resolvable through Micronaut DI. Behaviour tests run
 * against plain `ConfigDatabase()` instances.
 */
class StorageBeanWiringTest {

    @Test
    fun `ConfigDatabase resolves from the application context`() {
        ApplicationContext.run().use { ctx ->
            assertNotNull(ctx.getBean(ConfigDatabase::class.java))
        }
    }

    @Test
    fun `default DataSource bean fails fast before open is called`() {
        ApplicationContext.run().use { ctx ->
            // The @Factory delegates to ConfigDatabase.dataSource(), which throws
            // until open() is called. Resolving the bean before open should fail —
            // that proves the factory routes through the holder rather than
            // serving a stale auto-configured DataSource.
            assertThrows(BeanInstantiationException::class.java) {
                ctx.getBean(DataSource::class.java, Qualifiers.byName("default"))
            }
        }
    }
}
