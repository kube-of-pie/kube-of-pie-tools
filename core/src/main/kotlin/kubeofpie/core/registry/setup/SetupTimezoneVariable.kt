package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

@Singleton
class SetupTimezoneVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "setup.timezone"
    override val description: String =
        "tz database name applied via setup-timezone (e.g. 'UTC', 'Europe/Paris')."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
