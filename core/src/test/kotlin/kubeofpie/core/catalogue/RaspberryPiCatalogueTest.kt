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
class RaspberryPiCatalogueTest {

    @Test
    fun `supportedModelIds reflects the yaml files shipped under classpath raspberrypi`() {
        withCatalogue { catalogue ->
            assertEquals(listOf("pi4", "pi5"), catalogue.supportedModelIds())
        }
    }

    @Test
    fun `model parses architecture from the yaml payload`() {
        withCatalogue { catalogue ->
            assertEquals(
                RaspberryPiModel(id = "pi4", architecture = "aarch64"),
                catalogue.model("pi4"),
            )
            assertEquals(
                RaspberryPiModel(id = "pi5", architecture = "aarch64"),
                catalogue.model("pi5"),
            )
        }
    }

    @Test
    fun `supportedModels enumerates every parsed model`() {
        withCatalogue { catalogue ->
            assertEquals(
                listOf(
                    RaspberryPiModel(id = "pi4", architecture = "aarch64"),
                    RaspberryPiModel(id = "pi5", architecture = "aarch64"),
                ),
                catalogue.supportedModels(),
            )
        }
    }

    @Test
    fun `model returns null for an unknown id`() {
        withCatalogue { catalogue ->
            assertNull(catalogue.model("pi-not-a-thing"))
        }
    }

    private fun withCatalogue(block: (RaspberryPiCatalogue) -> Unit) {
        ApplicationContext.run().use { ctx ->
            block(ctx.getBean(RaspberryPiCatalogue::class.java))
        }
    }
}
