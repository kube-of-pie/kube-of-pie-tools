package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

@Singleton
class SetupNtpVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "setup.ntp"
    override val description: String =
        "NTP client configured via setup-ntp."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> =
        listOf("busybox", "chrony", "openntpd", "none")

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
