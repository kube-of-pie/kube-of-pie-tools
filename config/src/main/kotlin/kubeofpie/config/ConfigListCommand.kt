package kubeofpie.config

import jakarta.inject.Singleton
import java.util.concurrent.Callable
import kubeofpie.core.registry.ListableVariable
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.DatabasePath
import kubeofpie.core.storage.NoDatabaseException
import kubeofpie.core.storage.OpenMode
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters

@Singleton
@Command(
    name = "list",
    description = ["List allowed values of a variable, or identifiers of a listable family."],
    mixinStandardHelpOptions = true,
)
class ConfigListCommand(
    private val database: ConfigDatabase,
    private val registry: VariableRegistry,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Parameters(index = "0", paramLabel = "KEY", description = ["Dotted variable key (e.g. version.alpine)."])
    lateinit var key: String

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
        if (variable is ListableVariable) {
            for (id in variable.identifiers()) {
                println(id)
            }
        } else {
            val allowed = variable.allowedValues()
            if (allowed == null) {
                println("(unbounded)")
            } else {
                for (value in allowed) {
                    println(value)
                }
            }
        }
        return 0
    }
}
