package kubeofpie.core.registry

import jakarta.inject.Singleton
import kubeofpie.core.catalogue.AlpineCatalogue

@Singleton
class VersionAlpineVariable(
    private val storage: VariableStorage,
    private val catalogue: AlpineCatalogue,
) : Variable {

    override val key: String = "version.alpine"
    override val description: String =
        "Alpine Linux release used to flash the SD cards."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = catalogue.supportedVersions()

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
