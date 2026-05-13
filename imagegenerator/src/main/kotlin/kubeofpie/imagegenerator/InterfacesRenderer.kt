package kubeofpie.imagegenerator

import jakarta.inject.Singleton

/**
 * Builds the `/etc/network/interfaces` body fed to Alpine's `setup-interfaces -i`
 * by the first-boot script. The loopback stanza is always present; `wlan0` is
 * added only when the node's Wi-Fi flag is on, since the `unattended.sh` flow
 * brings up wired interfaces through Alpine's own DHCP defaults rather than
 * this file.
 */
@Singleton
class InterfacesRenderer {

    fun render(wifiEnabled: Boolean): String = buildString {
        append("auto lo\n")
        append("iface lo inet loopback\n")
        if (wifiEnabled) {
            append("\n")
            append("auto wlan0\n")
            append("iface wlan0 inet dhcp\n")
        }
    }
}
