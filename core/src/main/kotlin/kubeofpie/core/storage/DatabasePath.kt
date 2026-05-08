package kubeofpie.core.storage

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the absolute path to the cluster configuration database. Centralized in `:core`
 * so every CLI tool sees the same precedence rules:
 *
 * 1. The `--db <path>` CLI flag (when supplied).
 * 2. The `KUBE_OF_PIE_DB` environment variable.
 * 3. The default `kube-of-pie.db` in the current working directory.
 *
 * Returned paths are absolute and normalized so the user-visible feedback strings
 * (`created new database at <path>`, `updated <path>`) are stable regardless of how
 * the path was supplied.
 */
object DatabasePath {
    const val ENV_VAR: String = "KUBE_OF_PIE_DB"
    const val DEFAULT_FILENAME: String = "kube-of-pie.db"

    fun resolve(cliArg: String?, env: Map<String, String> = System.getenv()): Path {
        val raw = cliArg?.takeIf { it.isNotBlank() }
            ?: env[ENV_VAR]?.takeIf { it.isNotBlank() }
            ?: DEFAULT_FILENAME
        return Paths.get(raw).toAbsolutePath().normalize()
    }
}
