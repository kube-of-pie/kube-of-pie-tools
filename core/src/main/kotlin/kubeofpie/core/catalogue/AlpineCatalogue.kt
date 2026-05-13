package kubeofpie.core.catalogue

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile
import org.yaml.snakeyaml.Yaml

/**
 * Catalogue of Alpine releases the toolchain knows how to target. The supported set is
 * derived from the YAML files shipped under `classpath:alpine/<version>.yaml`: dropping a
 * new file there is the only step required to add support for a new release.
 *
 * Each YAML carries a templated `download_url` whose architecture component is the
 * placeholder `{arch}`; [downloadUrl] substitutes that placeholder with the architecture
 * the caller targets (typically obtained from [RaspberryPiCatalogue] via the configured
 * Pi model). YAML is parsed by SnakeYAML and bound to [AlpineYaml] through Micronaut
 * Serde via [JsonNode.from] and [ObjectMapper.readValueFromTree], keeping the type-safe
 * deserialization reflection-free under GraalVM `native-image`.
 */
@Singleton
class AlpineCatalogue(private val mapper: ObjectMapper) {

    /** Supported Alpine version identifiers (e.g. `"3.21"`), sorted ascending. */
    fun supportedVersions(): List<String> {
        val baseUrl = javaClass.classLoader.getResource(RESOURCE_DIR)
            ?: return emptyList()
        return when (baseUrl.protocol) {
            "file" -> listFromDirectory(baseUrl)
            "jar" -> listFromJar(baseUrl)
            else -> error("unsupported resource URL scheme: $baseUrl")
        }.sorted()
    }

    /**
     * Concrete download URL of the Alpine `rpi` archive for [version] on [architecture],
     * or `null` when [version] is not in [supportedVersions]. The architecture identifier
     * must match Alpine's path convention (`aarch64`, `armv7`, `armhf`) — exactly the
     * value [RaspberryPiCatalogue] returns per supported Pi model.
     */
    fun downloadUrl(version: String, architecture: String): String? =
        release(version)?.downloadUrl?.replace(ARCH_PLACEHOLDER, architecture)

    /**
     * Required `cmdline.txt` kernel arguments for [version], or `null` when [version] is
     * not in [supportedVersions]. The list is determined by the kernel that ships with
     * the chosen Alpine release (e.g. `cgroup_memory=1`, `cgroup_enable=memory` for the
     * cgroup driver Kubernetes needs); it is not user-configurable.
     */
    fun kernelArgs(version: String): List<String>? = release(version)?.kernelArgs

    /**
     * URL of the `headless.apkovl.tar.gz` overlay that drives Alpine's first-boot
     * unattended setup for [version], or `null` when [version] is not in
     * [supportedVersions]. Pinned per-release so an Alpine bump can shift the overlay
     * version in lockstep.
     */
    fun overlayUrl(version: String): String? = release(version)?.overlayUrl

    private fun release(version: String): AlpineYaml? {
        val resourceName = "$RESOURCE_DIR$version$YAML_SUFFIX"
        val raw = javaClass.classLoader.getResourceAsStream(resourceName) ?: return null
        val parsed: Any? = raw.use { Yaml().load(it) }
        return mapper.readValueFromTree(
            JsonNode.from(parsed),
            Argument.of(AlpineYaml::class.java),
        )
    }

    private fun listFromDirectory(url: URL): List<String> =
        Files.list(Paths.get(url.toURI())).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.endsWith(YAML_SUFFIX) }
                .map { it.removeSuffix(YAML_SUFFIX) }
                .toList()
        }

    private fun listFromJar(url: URL): List<String> {
        // jar URL form: jar:file:/path/to/app.jar!/alpine/
        val raw = url.path
        val bang = raw.indexOf("!/")
        require(bang >= 0) { "unexpected jar URL: $url" }
        val jarPath = raw.substring("file:".length, bang)
        return JarFile(jarPath).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith(RESOURCE_DIR) && it.endsWith(YAML_SUFFIX) }
                .map { it.removePrefix(RESOURCE_DIR).removeSuffix(YAML_SUFFIX) }
                .filter { it.isNotEmpty() && !it.contains('/') }
                .toList()
        }
    }

    private companion object {
        const val RESOURCE_DIR = "alpine/"
        const val YAML_SUFFIX = ".yaml"
        const val ARCH_PLACEHOLDER = "{arch}"
    }
}

/**
 * On-disk shape of `classpath:alpine/<version>.yaml`. The remaining fields
 * (repositories, dependencies, ...) ship in the YAML for future use and are
 * tolerated as unknown by the deserializer.
 */
@Serdeable
internal data class AlpineYaml(
    @param:JsonProperty("download_url") val downloadUrl: String,
    @param:JsonProperty("overlay_url") val overlayUrl: String,
    @param:JsonProperty("kernel_args") val kernelArgs: List<String>,
)
