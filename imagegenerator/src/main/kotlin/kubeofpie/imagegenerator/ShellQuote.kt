package kubeofpie.imagegenerator

/**
 * Escape values for embedding inside POSIX shell double-quoted strings. Used by
 * [GenerateCommand] before placing config values into [UnattendedModel], because the
 * `unattended.sh.ftl` template assumes its inputs are already safe to drop between
 * `"..."`.
 *
 * Inside double quotes only four characters retain shell meaning: `\`, `$`,
 * `` ` ``, and `"` (plus the newline-continuing backslash, handled by escaping
 * `\`). Backslashing those leaves the rest of the value untouched — including
 * embedded newlines, which Alpine's `setup-interfaces` reads line by line.
 */
object ShellQuote {

    fun escape(value: String): String = buildString(value.length + 4) {
        for (c in value) {
            when (c) {
                '\\', '"', '$', '`' -> append('\\').append(c)
                else -> append(c)
            }
        }
    }

    fun escapeOrNull(value: String?): String? = value?.let { escape(it) }
}
