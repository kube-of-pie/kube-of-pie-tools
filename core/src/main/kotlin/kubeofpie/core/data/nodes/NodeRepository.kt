package kubeofpie.core.data.nodes

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Micronaut Data JDBC repository over the `nodes` table. SQLite is not a formally
 * supported dialect, but [Dialect.ANSI] generates the CRUD shape the manager needs
 * (`findById`, `existsById`, `save`, `update`, `deleteById`). Per-field updates go
 * through `update(entity)` after a `copy()` in
 * [kubeofpie.core.business.nodes.NodeManager], so this interface stays minimal.
 */
@JdbcRepository(dialect = Dialect.ANSI)
interface NodeRepository : CrudRepository<NodeEntity, String> {

    /**
     * All rows in insertion order. SQLite assigns increasing rowids to inserted rows;
     * ordering by `ROWID` mirrors the previous "first added, first listed" behaviour.
     */
    @Query("SELECT * FROM \"nodes\" ORDER BY ROWID")
    fun listAllInInsertionOrder(): List<NodeEntity>
}
