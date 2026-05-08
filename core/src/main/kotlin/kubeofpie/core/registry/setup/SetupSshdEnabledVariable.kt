package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

@Singleton
class SetupSshdEnabledVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "setup.sshd.enabled"
    override val description: String =
        "Whether the OpenSSH daemon is started on the node at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = listOf("true", "false")

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
