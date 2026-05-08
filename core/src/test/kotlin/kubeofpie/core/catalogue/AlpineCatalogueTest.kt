package kubeofpie.core.catalogue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlpineCatalogueTest {

    @Test
    fun `supportedVersions reflects the yaml files shipped under classpath alpine`() {
        val catalogue = AlpineCatalogue()

        assertEquals(listOf("3.21"), catalogue.supportedVersions())
    }
}
