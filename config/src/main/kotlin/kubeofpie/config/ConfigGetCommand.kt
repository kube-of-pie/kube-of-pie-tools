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
import picocli.CommandLine.Parameters

@Singleton
@Command(
    name = "get",
    description = ["Print the current value of a configuration variable."],
    mixinStandardHelpOptions = true,
)
class ConfigGetCommand(
    private val database: ConfigDatabase,
    private val registry: VariableRegistry,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Parameters(index = "0", paramLabel = "KEY", description = ["Dotted variable key (e.g. version.alpine)."])
    lateinit var key: String

    @Option(
        names = ["--reveal"],
        description = ["Print sensitive values in the clear (default: redacted)."],
    )
    var reveal: Boolean = false

    override fun call(): Int {
        val path = DatabasePath.resolve(databaseOptions.path)
        try {
            database.open(path, OpenMode.READ_ONLY)
        } catch (e: NoDatabaseException) {
            System.err.println("no database at ${e.path}")
            return 2
        }
        val variable = registry.read(key)
        if (variable == null) {
            System.err.println("unknown variable: $key")
            return 2
        }
        val value = variable.read()
        if (value == null) {
            println("<unset>")
        } else if (variable.sensitive && !reveal) {
            println("<redacted>")
        } else {
            println(value)
        }
        return 0
    }
}
