package kubeofpie.imagegenerator

import jakarta.inject.Singleton
import java.nio.file.Path
import java.nio.file.Paths
import net.harawata.appdirs.AppDirsFactory

/**
 * Resolves the absolute path to the local asset cache used by [AssetFetcher].
 *
 * Precedence:
 * 1. Explicit `--cache-dir <path>` CLI flag (when supplied).
 * 2. `KUBE_OF_PIE_CACHE` environment variable.
 * 3. The OS-native user cache directory via `appdirs` under the app name
 *    `"kube-of-pie"` (Linux: `$XDG_CACHE_HOME/kube-of-pie`; macOS:
 *    `~/Library/Caches/kube-of-pie`; Windows: `%LOCALAPPDATA%\kube-of-pie\Cache`).
 *
 * Returned paths are absolute so log lines stay stable regardless of how the
 * caller supplied the override.
 */
@Singleton
class CacheLocation {

    fun resolve(override: String?, env: Map<String, String> = System.getenv()): Path {
        val raw = override?.takeIf { it.isNotBlank() }
            ?: env[ENV_VAR]?.takeIf { it.isNotBlank() }
            ?: AppDirsFactory.getInstance().getUserCacheDir(APP_NAME, null, null)
        return Paths.get(raw).toAbsolutePath().normalize()
    }

    companion object {
        const val ENV_VAR: String = "KUBE_OF_PIE_CACHE"
        const val APP_NAME: String = "kube-of-pie"
    }
}
