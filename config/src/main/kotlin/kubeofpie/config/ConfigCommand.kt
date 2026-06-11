package kubeofpie.config

import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import kotlin.system.exitProcess

@Command(
    name = "config",
    description = ["Author the kube-of-pie cluster configuration."],
    mixinStandardHelpOptions = true,
    subcommands = [
        ConfigListCommand::class,
        ConfigGetCommand::class,
        ConfigSetCommand::class,
        ConfigAddCommand::class,
        ConfigRemoveCommand::class,
    ],
)
class ConfigCommand : Runnable {

    @Option(
        names = ["-v", "--verbose"],
        description = ["Print info logs from Micronaut and all libraries on stderr."],
    )
    var verbose: Boolean = false

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}

fun main(args: Array<String>) {
    // Pre-parse --verbose: logback's root level reads ${KOP_LOG_LEVEL:-OFF} at context start,
    // which happens inside PicocliRunner.execute() before picocli sees the flag — so we set
    // the property here and strip the flag from argv to keep the picocli parser happy.
    if (args.any { it == "-v" || it == "--verbose" }) {
        System.setProperty("KOP_LOG_LEVEL", "INFO")
    }
    val filtered = args.filter { it != "-v" && it != "--verbose" }.toTypedArray()
    exitProcess(PicocliRunner.execute(ConfigCommand::class.java, *filtered))
}
