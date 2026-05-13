package kubeofpie.imagegenerator

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import jakarta.inject.Singleton
import java.io.StringWriter

/**
 * Renders the `unattended.sh` first-boot script from a typed [UnattendedModel] via
 * Apache FreeMarker. The [Configuration] instance is heavyweight — FreeMarker docs
 * explicitly call it out — so it is created once per singleton and reused.
 *
 * Template body assumes its model fields are already shell-escaped (see
 * [ShellQuote]). All template logic (conditional placeholders, per-user blocks)
 * lives in `unattended.sh.ftl`; this class is intentionally thin so the bulk of
 * rendering is exercised by [UnattendedRendererTest] without booting the rest of
 * the application.
 */
@Singleton
class UnattendedRenderer {

    private val config: Configuration = Configuration(Configuration.VERSION_2_3_34).apply {
        setClassLoaderForTemplateLoading(UnattendedRenderer::class.java.classLoader, "imagegenerator")
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
    }
    private val template = config.getTemplate("unattended.sh.ftl")

    fun render(model: UnattendedModel): String {
        val out = StringWriter()
        template.process(model, out)
        return out.toString()
    }
}
