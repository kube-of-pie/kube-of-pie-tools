package kubeofpie.config

import io.micronaut.configuration.picocli.PicocliRunner
import picocli.CommandLine.Command

@Command(
    name = "config",
    description = ["Author the kube-of-pie cluster configuration."],
    mixinStandardHelpOptions = true,
)
class ConfigCommand : Runnable {
    override fun run() {
    }
}

fun main(args: Array<String>) {
    PicocliRunner.run(ConfigCommand::class.java, *args)
}
