package kubeofpie.core.catalogue

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
 * Catalogue of Raspberry Pi models the toolchain knows how to target. The supported
 * set is derived from the YAML files shipped under `classpath:raspberrypi/<id>.yaml`:
 * dropping a new file there is the only step required to add support for a new
 * model. Each YAML payload carries the Alpine architecture (`aarch64`, `armv7`,
 * `armhf`) for that board so the Alpine download URL can be specialised for the
 * target Pi.
 *
 * YAML is parsed by SnakeYAML and bound to [RaspberryPiYaml] through Micronaut
 * Serde via [JsonNode.from] and [ObjectMapper.readValueFromTree], which keeps the
 * type-safe deserialization reflection-free under GraalVM `native-image`.
 */
@Singleton
class RaspberryPiCatalogue(private val mapper: ObjectMapper) {

    /** Supported Raspberry Pi identifiers (e.g. `"pi4"`), sorted ascending. */
    fun supportedModelIds(): List<String> {
        val baseUrl = javaClass.classLoader.getResource(RESOURCE_DIR)
            ?: return emptyList()
        return when (baseUrl.protocol) {
            "file" -> listFromDirectory(baseUrl)
            "jar" -> listFromJar(baseUrl)
            else -> error("unsupported resource URL scheme: $baseUrl")
        }.sorted()
    }

    /** Every supported model with its parsed payload, sorted by id. */
    fun supportedModels(): List<RaspberryPiModel> = supportedModelIds().mapNotNull(::model)

    /** The model for [id], or `null` when no `<id>.yaml` exists on the classpath. */
    fun model(id: String): RaspberryPiModel? {
        val resourceName = "$RESOURCE_DIR$id$YAML_SUFFIX"
        val raw = javaClass.classLoader.getResourceAsStream(resourceName) ?: return null
        val parsed: Any? = raw.use { Yaml().load(it) }
        val payload = mapper.readValueFromTree(
            JsonNode.from(parsed),
            Argument.of(RaspberryPiYaml::class.java),
        )
        return RaspberryPiModel(id = id, architecture = payload.architecture)
    }

    private fun listFromDirectory(url: URL): List<String> =
        Files.list(Paths.get(url.toURI())).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.endsWith(YAML_SUFFIX) }
                .map { it.removeSuffix(YAML_SUFFIX) }
                .toList()
        }

    private fun listFromJar(url: URL): List<String> {
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
        const val RESOURCE_DIR = "raspberrypi/"
        const val YAML_SUFFIX = ".yaml"
    }
}

/** A supported Raspberry Pi model and the Alpine architecture its CPU runs. */
data class RaspberryPiModel(
    val id: String,
    val architecture: String,
)

/**
 * On-disk shape of `classpath:raspberrypi/<id>.yaml`. The id comes from the file
 * name (matching [AlpineCatalogue]'s convention) so the YAML body only carries
 * the per-model payload.
 */
@Serdeable
internal data class RaspberryPiYaml(val architecture: String)
