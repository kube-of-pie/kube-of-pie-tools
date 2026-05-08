package kubeofpie.core.registry.setup

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

/**
 * Extra `cmdline.txt` arguments appended at first boot (e.g. `cgroup_memory=1`,
 * `cgroup_enable=memory`).
 *
 * The list is stored as a JSON-array-of-strings string in the single `value`
 * column, so the existing `(key, value)` schema can carry it without a
 * migration. Callers see and write JSON; [write] validates the shape strictly
 * (each element must be a JSON string, not a coerced number) before
 * persisting.
 */
@Singleton
class SetupBootAdditionalKernelArgsVariable(
    private val storage: VariableStorage,
) : Variable {

    override val key: String = "setup.boot.additional_kernel_args"
    override val description: String =
        "Extra Linux kernel cmdline arguments, JSON array of strings " +
            "(e.g. [\"cgroup_memory=1\",\"cgroup_enable=memory\"])."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) {
        val node = try {
            mapper.readTree(value)
        } catch (e: JacksonException) {
            throw badShape(value, e)
        }
        if (!node.isArray || !node.all { it.isTextual }) {
            throw badShape(value, null)
        }
        storage.write(key, mapper.writeValueAsString(node))
    }

    private fun badShape(value: String, cause: Throwable?): IllegalArgumentException =
        IllegalArgumentException(
            "value for $key must be a JSON array of strings, got: $value",
            cause,
        )

    private companion object {
        private val mapper: ObjectMapper = ObjectMapper()
    }
}
