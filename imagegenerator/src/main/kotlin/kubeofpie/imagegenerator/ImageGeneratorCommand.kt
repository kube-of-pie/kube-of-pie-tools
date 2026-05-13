package kubeofpie.imagegenerator

import io.micronaut.configuration.picocli.PicocliRunner
import kotlin.system.exitProcess
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

@Command(
    name = "imagegenerator",
    description = ["Stage Raspberry Pi disk images from the kube-of-pie cluster configuration."],
    mixinStandardHelpOptions = true,
    subcommands = [
        GenerateCommand::class,
    ],
)
class ImageGeneratorCommand : Runnable {

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}

fun main(args: Array<String>) {
    exitProcess(PicocliRunner.execute(ImageGeneratorCommand::class.java, *args))
}
