package kubeofpie.config

import jakarta.inject.Singleton
import java.util.concurrent.Callable
import kubeofpie.core.registry.ListableVariable
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.DatabasePath
import kubeofpie.core.storage.OpenMode
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters

@Singleton
@Command(
    name = "add",
    description = ["Add an identifier to a listable family (e.g. users, nodes)."],
    mixinStandardHelpOptions = true,
)
class ConfigAddCommand(
    private val database: ConfigDatabase,
    private val registry: VariableRegistry,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Parameters(index = "0", paramLabel = "KEY", description = ["Listable family key (e.g. users, nodes)."])
    lateinit var key: String

    @Parameters(
        index = "1",
        paramLabel = "ID",
        arity = "0..1",
        description = ["Identifier to add (required for users; optional for nodes — auto-assigned)."],
    )
    var id: String? = null

    override fun call(): Int {
        val path = DatabasePath.resolve(databaseOptions.path)
        val result = database.open(path, OpenMode.READ_WRITE)
        val variable = registry.read(key)
        if (variable == null) {
            System.err.println("unknown variable: $key")
            return 2
        }
        if (variable !is ListableVariable) {
            System.err.println("$key is not a listable family")
            return 2
        }
        return try {
            val assigned = variable.add(id)
            if (result.createdNew) {
                println("created new database at ${result.path}")
            }
            println("added $key.$assigned")
            0
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            2
        }
    }
}
