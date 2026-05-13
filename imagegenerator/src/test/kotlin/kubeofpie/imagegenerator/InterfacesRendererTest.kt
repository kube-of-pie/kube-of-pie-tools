package kubeofpie.imagegenerator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterfacesRendererTest {

    private val renderer = InterfacesRenderer()

    @Test
    fun `renders loopback only when wifi is disabled`() {
        assertEquals(
            """
            auto lo
            iface lo inet loopback

            """.trimIndent(),
            renderer.render(wifiEnabled = false),
        )
    }

    @Test
    fun `appends wlan0 stanza when wifi is enabled`() {
        assertEquals(
            """
            auto lo
            iface lo inet loopback

            auto wlan0
            iface wlan0 inet dhcp

            """.trimIndent(),
            renderer.render(wifiEnabled = true),
        )
    }
}
