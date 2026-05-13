package kubeofpie.imagegenerator

import io.micronaut.context.ApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.business.users.UserManager
import kubeofpie.core.business.versions.AlpineVersionManager
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Boots a real ApplicationContext with a read-write temp DB so we can drive the
 * managers (which validate inputs) to seed state, then resolve Preflight from
 * the same context to exercise each validation branch.
 */
class PreflightTest {

    @Test
    fun `unknown node id is rejected`(@TempDir tmp: Path) = withSeed(tmp) { _, _, _, preflight ->
        val outDir = tmp.resolve("out")
        val err = preflight.check(Preflight.Input("ghost", outDir, force = false))
        assertTrue(err?.message?.contains("node not registered") == true, err?.message)
    }

    @Test
    fun `node without a model is rejected`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        versions.set("3.21")
        val err = preflight.check(Preflight.Input("master", tmp.resolve("out"), force = false))
        assertTrue(err?.message?.contains("has no model set") == true, err?.message)
    }

    @Test
    fun `unset version_alpine is rejected`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, _, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        val err = preflight.check(Preflight.Input("master", tmp.resolve("out"), force = false))
        assertTrue(err?.message?.contains("version.alpine is not set") == true, err?.message)
    }

    @Test
    fun `wifi enabled without ssid is rejected`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        nodes.setWifiEnabled("master", true)
        nodes.setWifiPassphrase("master", "secret")
        versions.set("3.21")
        val err = preflight.check(Preflight.Input("master", tmp.resolve("out"), force = false))
        assertTrue(err?.message?.contains("wifi is enabled") == true && err?.message?.contains("ssid") == true, err?.message)
    }

    @Test
    fun `wifi enabled without passphrase is rejected`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        nodes.setWifiEnabled("master", true)
        nodes.setWifiSsid("master", "homewifi")
        versions.set("3.21")
        val err = preflight.check(Preflight.Input("master", tmp.resolve("out"), force = false))
        assertTrue(err?.message?.contains("passphrase") == true, err?.message)
    }

    @Test
    fun `non-empty output directory is rejected without --force`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        versions.set("3.21")
        val outDir = tmp.resolve("out")
        Files.createDirectories(outDir)
        Files.writeString(outDir.resolve("placeholder"), "x")

        val err = preflight.check(Preflight.Input("master", outDir, force = false))
        assertTrue(err?.message?.contains("not empty") == true, err?.message)
    }

    @Test
    fun `non-empty output directory passes with --force`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        versions.set("3.21")
        val outDir = tmp.resolve("out")
        Files.createDirectories(outDir)
        Files.writeString(outDir.resolve("placeholder"), "x")

        assertNull(preflight.check(Preflight.Input("master", outDir, force = true)))
    }

    @Test
    fun `fully valid input passes`(@TempDir tmp: Path) = withSeed(tmp) { nodes, _, versions, preflight ->
        nodes.add("master")
        nodes.setModel("master", "pi4")
        versions.set("3.21")
        assertEquals(null, preflight.check(Preflight.Input("master", tmp.resolve("out"), force = false)))
    }

    private fun withSeed(
        tmp: Path,
        block: (NodeManager, UserManager, AlpineVersionManager, Preflight) -> Unit,
    ) {
        ApplicationContext.run().use { ctx ->
            val db = ctx.getBean(ConfigDatabase::class.java)
            db.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val nodes = ctx.getBean(NodeManager::class.java)
            val users = ctx.getBean(UserManager::class.java)
            val versions = ctx.getBean(AlpineVersionManager::class.java)
            val preflight = ctx.getBean(Preflight::class.java)
            block(nodes, users, versions, preflight)
        }
    }
}
