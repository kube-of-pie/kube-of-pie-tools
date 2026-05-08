package kubeofpie.config

import jakarta.inject.Singleton
import java.util.concurrent.Callable
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.DatabasePath
import kubeofpie.core.storage.NoDatabaseException
import kubeofpie.core.storage.OpenMode
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

@Singleton
@Command(
    name = "list",
    description = ["List configuration variables and their metadata."],
    mixinStandardHelpOptions = true,
)
class ConfigListCommand(
    private val database: ConfigDatabase,
    private val registry: VariableRegistry,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Option(
        names = ["--prefix"],
        paramLabel = "<p>",
        description = ["Only list variables whose key starts with <p>."],
    )
    var prefix: String? = null

    @Option(
        names = ["--writable-only"],
        description = ["Hide derived, catalogue-pinned, and generated variables."],
    )
    var writableOnly: Boolean = false

    override fun call(): Int {
        val path = DatabasePath.resolve(databaseOptions.path)
        try {
            database.open(path, OpenMode.READ_ONLY)
        } catch (e: NoDatabaseException) {
            System.err.println("no database at ${e.path}")
            return 2
        }
        for (variable in registry.list(prefix, writableOnly)) {
            val value = when {
                variable.sensitive -> "<redacted>"
                else -> variable.read() ?: "<unset>"
            }
            val allowed = variable.allowedValues()?.joinToString(", ", "[", "]") ?: "(unbounded)"
            val flags = buildList {
                if (!variable.writable) add("read-only")
                if (variable.sensitive) add("sensitive")
            }.joinToString(", ").ifEmpty { "writable" }
            println("${variable.key} = $value")
            println("  ${variable.description}")
            println("  allowed: $allowed")
            println("  flags:   $flags")
        }
        return 0
    }
}
