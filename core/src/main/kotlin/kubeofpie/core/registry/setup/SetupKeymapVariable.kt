package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

@Singleton
class SetupKeymapVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "setup.keymap"
    override val description: String =
        "Console keyboard layout passed to setup-keymap (e.g. 'fr fr', 'us us')."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
