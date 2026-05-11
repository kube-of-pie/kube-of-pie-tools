package kubeofpie.core.business.versions

import jakarta.inject.Provider
import jakarta.inject.Singleton
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.data.versions.VersionEntity
import kubeofpie.core.data.versions.VersionRepository

/**
 * Owns the cluster's pinned Alpine release. Both the CLI's `version.alpine` variable
 * adapter and any future web UI go through this manager rather than the generic
 * variable registry; the manager talks to a Micronaut Data JDBC repository
 * underneath and validates writes against the [AlpineCatalogue].
 *
 * `repositoryProvider` is a [Provider] so the repository — and the `DataSource` it
 * depends on — is resolved lazily, after the CLI has called
 * [kubeofpie.core.storage.ConfigDatabase.open]. Resolving eagerly would fail because
 * the data source factory throws "database not opened" until then.
 */
@Singleton
class AlpineVersionManager(
    private val repositoryProvider: Provider<VersionRepository>,
    private val catalogue: AlpineCatalogue,
) {

    private val repository: VersionRepository get() = repositoryProvider.get()

    /** Current Alpine version, or `null` when nothing has been pinned yet. */
    fun get(): String? = repository.findById(ID).orElse(null)?.version

    /**
     * Pin [value] as the cluster's Alpine version. Throws [IllegalArgumentException]
     * when [value] is not in [allowedVersions]. Upserts the single `alpine` row.
     */
    fun set(value: String) {
        val allowed = allowedVersions()
        require(value in allowed) {
            "alpine version '$value' is not in the supported set $allowed"
        }
        val entity = VersionEntity(id = ID, version = value)
        if (repository.existsById(ID)) {
            repository.update(entity)
        } else {
            repository.save(entity)
        }
    }

    /** Supported Alpine version identifiers, sourced from [AlpineCatalogue]. */
    fun allowedVersions(): List<String> = catalogue.supportedVersions()

    private companion object {
        private const val ID = "alpine"
    }
}
