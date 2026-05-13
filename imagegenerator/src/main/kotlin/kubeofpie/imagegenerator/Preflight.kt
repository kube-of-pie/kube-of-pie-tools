package kubeofpie.imagegenerator

import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.business.versions.AlpineVersionManager
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.catalogue.RaspberryPiCatalogue

/**
 * Validation pass executed by [GenerateCommand] before any network or disk
 * activity. Each check is ordered so the message names the earliest fixable
 * problem; the first failure short-circuits.
 *
 * Returns `null` on success, or a [PreflightError] carrying the message the CLI
 * should print to stderr.
 */
@Singleton
class Preflight(
    private val nodeManager: NodeManager,
    private val alpineVersionManager: AlpineVersionManager,
    private val alpineCatalogue: AlpineCatalogue,
    private val raspberryPiCatalogue: RaspberryPiCatalogue,
) {

    fun check(input: Input): PreflightError? {
        val node = nodeManager.get(input.nodeId)
            ?: return PreflightError("node not registered: ${input.nodeId} (run 'config add nodes ${input.nodeId}')")

        val model = node.model
            ?: return PreflightError("node ${input.nodeId} has no model set (run 'config set nodes.${input.nodeId}.model <model>')")

        val piModel = raspberryPiCatalogue.model(model)
            ?: return PreflightError("unsupported model: $model")

        val version = alpineVersionManager.get()
            ?: return PreflightError("version.alpine is not set (run 'config set version.alpine <version>')")

        if (version !in alpineCatalogue.supportedVersions()) {
            return PreflightError("unsupported alpine version: $version")
        }

        if (alpineCatalogue.downloadUrl(version, piModel.architecture) == null) {
            return PreflightError("no download_url configured for alpine $version (${piModel.architecture})")
        }

        if (alpineCatalogue.overlayUrl(version) == null) {
            return PreflightError("no overlay_url configured for alpine $version")
        }

        if (node.wifiEnabled == true) {
            if (node.wifiSsid.isNullOrBlank()) {
                return PreflightError("wifi is enabled for node ${input.nodeId} but nodes.${input.nodeId}.network.wifi.ssid is not set")
            }
            if (node.wifiPassphrase.isNullOrBlank()) {
                return PreflightError("wifi is enabled for node ${input.nodeId} but nodes.${input.nodeId}.network.wifi.passphrase is not set")
            }
        }

        val outErr = validateOutDir(input.outDir, input.force)
        if (outErr != null) return outErr

        return null
    }

    private fun validateOutDir(outDir: Path, force: Boolean): PreflightError? {
        if (!Files.exists(outDir)) return null
        if (!Files.isDirectory(outDir)) {
            return PreflightError("output path exists and is not a directory: $outDir")
        }
        val nonEmpty = Files.newDirectoryStream(outDir).use { it.iterator().hasNext() }
        if (nonEmpty && !force) {
            return PreflightError("output directory not empty: $outDir (pass --force to overwrite)")
        }
        return null
    }

    data class Input(
        val nodeId: String,
        val outDir: Path,
        val force: Boolean,
    )
}

data class PreflightError(val message: String)
