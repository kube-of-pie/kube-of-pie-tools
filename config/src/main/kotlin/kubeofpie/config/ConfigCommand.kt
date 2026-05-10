package kubeofpie.config

import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
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

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}

fun main(args: Array<String>) {
    exitProcess(PicocliRunner.execute(ConfigCommand::class.java, *args))
}
