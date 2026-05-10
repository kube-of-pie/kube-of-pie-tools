package kubeofpie.core.catalogue

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The catalogue depends on a Micronaut Serde [io.micronaut.serde.ObjectMapper], so the
 * test resolves it through a fresh [ApplicationContext] rather than `@MicronautTest`:
 * the storage `@Factory` exposes a `default` `DataSource` that fails until
 * `ConfigDatabase.open()` runs, and `@MicronautTest`'s transactional listener would
 * eagerly resolve it.
 */
class AlpineCatalogueTest {

    @Test
    fun `supportedVersions reflects the yaml files shipped under classpath alpine`() {
        withCatalogue { catalogue ->
            assertEquals(listOf("3.21"), catalogue.supportedVersions())
        }
    }

    @Test
    fun `downloadUrl substitutes the architecture placeholder`() {
        withCatalogue { catalogue ->
            assertEquals(
                "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-rpi-3.21.3-aarch64.tar.gz",
                catalogue.downloadUrl("3.21", "aarch64"),
            )
            assertEquals(
                "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/alpine-rpi-3.21.3-armv7.tar.gz",
                catalogue.downloadUrl("3.21", "armv7"),
            )
        }
    }

    @Test
    fun `downloadUrl returns null for an unknown version`() {
        withCatalogue { catalogue ->
            assertNull(catalogue.downloadUrl("3.99", "aarch64"))
        }
    }

    private fun withCatalogue(block: (AlpineCatalogue) -> Unit) {
        ApplicationContext.run().use { ctx ->
            block(ctx.getBean(AlpineCatalogue::class.java))
        }
    }
}
