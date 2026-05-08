package kubeofpie.core.storage

/**
 * Mode in which a CLI process opens the configuration database.
 *
 * `:config` is the only tool that uses [READ_WRITE]; the read-only generators
 * (`:imagegenerator`, `:inventorygenerator`) always pass [READ_ONLY] so they
 * never create files or run migrations.
 */
enum class OpenMode { READ_WRITE, READ_ONLY }
