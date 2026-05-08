package kubeofpie.core.storage

import java.nio.file.Path

/**
 * Thrown when a [OpenMode.READ_ONLY] open is attempted against a path that does not
 * exist. Read-only CLI commands translate this into the spec's `no database at <path>`
 * message at the CLI boundary instead of letting the stack trace leak.
 */
class NoDatabaseException(val path: Path) : RuntimeException("no database at $path")
