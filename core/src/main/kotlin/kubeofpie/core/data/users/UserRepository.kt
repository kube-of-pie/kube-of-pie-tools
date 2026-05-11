package kubeofpie.core.data.users

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ANSI)
interface UserRepository : CrudRepository<UserEntity, String> {

    /**
     * All rows in insertion order. SQLite assigns increasing rowids to inserted rows;
     * ordering by `ROWID` mirrors the previous "first added, first listed" behaviour.
     */
    @Query("SELECT * FROM \"users\" ORDER BY ROWID")
    fun listAllInInsertionOrder(): List<UserEntity>
}
