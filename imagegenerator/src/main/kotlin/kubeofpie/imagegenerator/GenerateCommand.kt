package kubeofpie.imagegenerator

import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Callable
import kubeofpie.core.business.nodes.Node
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.business.users.UserManager
import kubeofpie.core.business.versions.AlpineVersionManager
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.catalogue.RaspberryPiCatalogue
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.DatabasePath
import kubeofpie.core.storage.NoDatabaseException
import kubeofpie.core.storage.OpenMode
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

@Singleton
@Command(
    name = "generate",
    description = ["Stage a per-node Raspberry Pi image directory (Alpine rootfs + headless overlay + unattended.sh)."],
    mixinStandardHelpOptions = true,
)
class GenerateCommand(
    private val database: ConfigDatabase,
    private val nodeManager: NodeManager,
    private val userManager: UserManager,
    private val alpineVersionManager: AlpineVersionManager,
    private val alpineCatalogue: AlpineCatalogue,
    private val raspberryPiCatalogue: RaspberryPiCatalogue,
    private val variableRegistry: VariableRegistry,
    private val preflight: Preflight,
    private val interfacesRenderer: InterfacesRenderer,
    private val unattendedRenderer: UnattendedRenderer,
    private val cacheLocation: CacheLocation,
    private val assetFetcher: AssetFetcher,
    private val tarballExtractor: TarballExtractor,
) : Callable<Int> {

    @Mixin
    lateinit var databaseOptions: DatabaseOptions

    @Option(
        names = ["--node"],
        paramLabel = "<id>",
        required = true,
        description = ["Node id (as registered with 'config add nodes <id>')."],
    )
    lateinit var nodeId: String

    @Option(
        names = ["--out"],
        paramLabel = "<dir>",
        required = true,
        description = ["Output staging directory. Refuses to overwrite a non-empty dir unless --force."],
    )
    lateinit var outPath: String

    @Option(
        names = ["--cache-dir"],
        paramLabel = "<dir>",
        description = [
            "Directory holding the cached Alpine tarball and overlay.",
            "Falls back to the \$${CacheLocation.ENV_VAR} environment variable, then to the OS-native user cache dir.",
        ],
    )
    var cacheDirOverride: String? = null

    @Option(
        names = ["--offline"],
        description = ["Fail if any required asset is not already in the cache."],
    )
    var offline: Boolean = false

    @Option(
        names = ["--force"],
        description = ["Delete the contents of --out before extracting."],
    )
    var force: Boolean = false

    /** Test hook: override the resolved Alpine tarball URL (typically a `file://` fixture). */
    @Option(names = ["--alpine-url"], hidden = true)
    var alpineUrlOverride: String? = null

    /** Test hook: override the resolved overlay URL (typically a `file://` fixture). */
    @Option(names = ["--overlay-url"], hidden = true)
    var overlayUrlOverride: String? = null

    override fun call(): Int {
        val dbPath = DatabasePath.resolve(databaseOptions.path)
        try {
            database.open(dbPath, OpenMode.READ_ONLY)
        } catch (e: NoDatabaseException) {
            System.err.println("no database at ${e.path}")
            return 2
        }

        val outDir = Paths.get(outPath).toAbsolutePath().normalize()

        val error = preflight.check(Preflight.Input(nodeId, outDir, force))
        if (error != null) {
            System.err.println(error.message)
            return 2
        }

        val node = nodeManager.get(nodeId)!!
        val version = alpineVersionManager.get()!!
        val piModel = raspberryPiCatalogue.model(node.model!!)!!

        val tarballUrl = alpineUrlOverride ?: alpineCatalogue.downloadUrl(version, piModel.architecture)!!
        val overlayUrl = overlayUrlOverride ?: alpineCatalogue.overlayUrl(version)!!

        val cacheRoot = cacheLocation.resolve(cacheDirOverride)
        val tarballPath = try {
            assetFetcher.get(tarballUrl, cacheRoot.resolve("alpine"), offline)
        } catch (e: OfflineCacheMissException) {
            System.err.println(e.message)
            return 2
        } catch (e: DownloadException) {
            System.err.println(e.message)
            return 1
        }
        val overlayPath = try {
            assetFetcher.get(overlayUrl, cacheRoot.resolve("overlay"), offline)
        } catch (e: OfflineCacheMissException) {
            System.err.println(e.message)
            return 2
        } catch (e: DownloadException) {
            System.err.println(e.message)
            return 1
        }

        prepareOutDir(outDir)

        tarballExtractor.extract(tarballPath, outDir)
        Files.copy(
            overlayPath,
            outDir.resolve("headless.apkovl.tar.gz"),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val model = buildModel(node)
        val script = unattendedRenderer.render(model)
        val scriptPath = outDir.resolve("unattended.sh")
        Files.writeString(scriptPath, script)
        applyExecutable(scriptPath)

        println("staged $nodeId at $outDir (alpine $version, ${piModel.architecture})")
        return 0
    }

    private fun prepareOutDir(outDir: Path) {
        if (!Files.exists(outDir)) {
            Files.createDirectories(outDir)
            return
        }
        if (force) {
            Files.walk(outDir).use { stream ->
                stream.sorted(Comparator.reverseOrder())
                    .filter { it != outDir }
                    .forEach(Files::delete)
            }
        }
    }

    private fun buildModel(node: Node): UnattendedModel {
        val users = userManager.list().map { name ->
            val user = userManager.get(name)!!
            UserEntry(
                name = name,
                password = ShellQuote.escapeOrNull(user.password),
                sshPublicKey = if (user.sshEnabled == true) ShellQuote.escapeOrNull(user.sshPublicKey) else null,
            )
        }
        val hostname = node.hostname ?: node.id
        val interfaces = interfacesRenderer.render(node.wifiEnabled == true)
        val sshdEnabled = variableRegistry.read("setup.sshd.enabled")?.read() == "true"

        return UnattendedModel(
            keymap = ShellQuote.escapeOrNull(variableRegistry.read("setup.keymap")?.read()),
            hostname = ShellQuote.escape(hostname),
            interfaces = ShellQuote.escape(interfaces),
            wifiEnabled = node.wifiEnabled == true,
            wifiSsid = ShellQuote.escapeOrNull(node.wifiSsid),
            wifiPassphrase = ShellQuote.escapeOrNull(node.wifiPassphrase),
            dns = ShellQuote.escapeOrNull(node.dns),
            users = users,
            sshdEnabled = sshdEnabled,
            timezone = ShellQuote.escapeOrNull(variableRegistry.read("setup.timezone")?.read()),
            ntp = ShellQuote.escapeOrNull(variableRegistry.read("setup.ntp")?.read()),
            additionalKernelArgs = ShellQuote.escapeOrNull(nodeManager.kernelArgs(node.id)),
        )
    }

    private fun applyExecutable(path: Path) {
        val view = Files.getFileAttributeView(
            path,
            java.nio.file.attribute.PosixFileAttributeView::class.java,
        ) ?: return
        view.setPermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    }
}
