package kubeofpie.core.business.versions

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AlpineVersionManagerTest {

    @Test
    fun `get returns null before any set`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertNull(manager.get())
    }

    @Test
    fun `set then get returns the persisted value`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.set("3.21")
        assertEquals("3.21", manager.get())
    }

    @Test
    fun `set rejects versions outside the catalogue`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        val ex = assertThrows(IllegalArgumentException::class.java) { manager.set("3.99") }
        assertTrue(ex.message!!.contains("not in the supported set"), ex.message)
        assertNull(manager.get())
    }

    @Test
    fun `set twice updates rather than duplicating`(@TempDir tmp: Path) = withManager(tmp) { manager, ctx ->
        manager.set("3.21")
        manager.set("3.21")

        ctx.getBean(ConfigDatabase::class.java).connection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM versions WHERE id = ?").use { stmt ->
                stmt.setString(1, "alpine")
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `allowedVersions mirrors the catalogue`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertEquals(listOf("3.21"), manager.allowedVersions())
    }

    private fun withManager(tmp: Path, block: (AlpineVersionManager) -> Unit) {
        withManager(tmp) { manager, _ -> block(manager) }
    }

    private fun withManager(tmp: Path, block: (AlpineVersionManager, ApplicationContext) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            block(ctx.getBean(AlpineVersionManager::class.java), ctx)
        }
    }
}
