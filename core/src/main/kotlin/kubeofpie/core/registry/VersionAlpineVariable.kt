package kubeofpie.core.registry

import jakarta.inject.Singleton
import kubeofpie.core.business.versions.AlpineVersionManager

@Singleton
class VersionAlpineVariable(
    private val manager: AlpineVersionManager,
) : Variable {

    override val key: String = "version.alpine"
    override val description: String =
        "Alpine Linux release used to flash the SD cards."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = manager.allowedVersions()

    override fun read(): String? = manager.get()

    override fun write(value: String) = manager.set(value)
}
