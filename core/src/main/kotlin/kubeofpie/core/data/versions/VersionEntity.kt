package kubeofpie.core.data.versions

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

/**
 * Persistence row in the `versions` SQLite table. One row per component the toolchain
 * pins a version for (today: only `alpine`; later: `kubernetes`, `containerd`, ...).
 * Public callers see the version string directly through
 * [kubeofpie.core.business.versions.AlpineVersionManager]; this entity lives next to
 * its [VersionRepository] in the `data.versions` package.
 */
@MappedEntity("versions")
data class VersionEntity(
    @field:Id val id: String,
    val version: String,
)
