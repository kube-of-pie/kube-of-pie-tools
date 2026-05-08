package kubeofpie.config

import jakarta.inject.Singleton
import java.util.concurrent.Callable
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.DatabasePath
import kubeofpie.core.storage.OpenMode
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters

@Singleton
@Command(
    name = "set",
    description = ["Set a configuration variable."],
    mixinStandardHelpOptions = true,
)
class ConfigSetCommand(
    private val database: ConfigDatabase,
    private val registry: VariableRegistry,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Parameters(index = "0", paramLabel = "KEY", description = ["Dotted variable key (e.g. version.alpine)."])
    lateinit var key: String

    @Parameters(index = "1", paramLabel = "VALUE", description = ["New value for KEY."])
    lateinit var value: String

    override fun call(): Int {
        val path = DatabasePath.resolve(databaseOptions.path)
        val result = database.open(path, OpenMode.READ_WRITE)
        return try {
            registry.write(key, value)
            if (result.createdNew) {
                println("created new database at ${result.path}")
            } else {
                println("updated ${result.path}")
            }
            0
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            2
        }
    }
}
