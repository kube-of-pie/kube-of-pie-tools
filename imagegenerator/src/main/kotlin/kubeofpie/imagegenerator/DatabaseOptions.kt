package kubeofpie.imagegenerator

import kubeofpie.core.storage.DatabasePath
import picocli.CommandLine.Option

/**
 * Picocli `@Mixin` for the shared `--db` flag. Duplicated from `:config` rather
 * than promoted to `:core` so that the shared library keeps no dependency on
 * picocli.
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
