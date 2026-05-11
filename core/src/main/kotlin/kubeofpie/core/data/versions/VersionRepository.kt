package kubeofpie.core.data.versions

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Micronaut Data JDBC repository over the `versions` table. SQLite is not a formally
 * supported dialect, but [Dialect.ANSI] generates the CRUD shape the manager needs
 * (`findById`, `existsById`, `save`, `update`, `deleteById`). Lookups are always by
 * the component id (e.g. `"alpine"`), so the interface stays at the `CrudRepository`
 * default.
 */
@JdbcRepository(dialect = Dialect.ANSI)
interface VersionRepository : CrudRepository<VersionEntity, String>
