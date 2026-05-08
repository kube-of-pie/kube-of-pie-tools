package kubeofpie.core.storage

import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton
import javax.sql.DataSource

/**
 * Exposes the open [ConfigDatabase]'s [DataSource] as the Micronaut Data default
 * datasource bean. Future `@JdbcRepository(dataSource = "default")` interfaces
 * resolve through this factory transparently. Resolution is lazy — the bean is
 * only built when something injects [DataSource], and injecting before
 * [ConfigDatabase.open] has been called fails fast with `database not opened`.
 */
@Factory
class DataSourceFactory(private val database: ConfigDatabase) {

    @Singleton
    @Named("default")
    fun dataSource(): DataSource = database.dataSource()
}
