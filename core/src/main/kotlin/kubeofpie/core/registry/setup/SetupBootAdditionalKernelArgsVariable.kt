package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VersionAlpineVariable

/**
 * Required `cmdline.txt` arguments appended at first boot. The set is dictated by the
 * kernel that ships with the configured `version.alpine` (e.g. `cgroup_memory=1`,
 * `cgroup_enable=memory` for Kubernetes' cgroup driver), so the value is read-only and
 * sourced from [AlpineCatalogue].
 *
 * The returned string is in `cmdline.txt` form — args separated by single spaces — so a
 * consumer can splice it directly into the boot config without parsing.
 */
@Singleton
class SetupBootAdditionalKernelArgsVariable(
    private val alpine: VersionAlpineVariable,
    private val catalogue: AlpineCatalogue,
) : Variable {

    override val key: String = "setup.boot.additional_kernel_args"
    override val description: String =
        "Required Linux kernel cmdline arguments for the configured version.alpine, " +
            "space-separated as they appear in cmdline.txt."
    override val writable: Boolean = false
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? {
        val version = alpine.read() ?: return null
        val args = catalogue.kernelArgs(version) ?: return null
        return args.joinToString(" ")
    }

    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is derived from version.alpine; set version.alpine instead.",
    )
}
