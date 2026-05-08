package kubeofpie.config

import kubeofpie.core.storage.DatabasePath
import picocli.CommandLine.Option

/**
 * Picocli `@Mixin` shared by every `config` subcommand. Carries the global `--db` flag and
 * defers to [DatabasePath] for the precedence rules (flag > `KUBE_OF_PIE_DB` > default).
 */
class DatabaseOptions {

    @Option(
        names = ["--db"],
        paramLabel = "<path>",
        description = [
            "Path to the cluster configuration database.",
            "Falls back to the \$${DatabasePath.ENV_VAR} environment variable, then to ./${DatabasePath.DEFAULT_FILENAME}.",
        ],
    )
    var path: String? = null
}
