package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.inference.GradlePluginInference
import io.deepmedia.tools.deployer.inference.Inference
import io.deepmedia.tools.deployer.inference.KotlinInference
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

open class Content @Inject constructor(private val objects: ObjectFactory) : ComponentScope {

    val inherit = objects.property<Boolean>().convention(true)

    private val inheritedComponents = objects.domainObjectSet(Component::class)
    private val declaredComponents = objects.domainObjectSet(Component::class)

    val components get() = if (inherit.get()) inheritedComponents else declaredComponents

    override fun component(action: Action<Component>): Component {
        val component: Component = objects.newInstance()
        action.execute(component)
        declaredComponents.add(component)
        return component
    }

    private val inferenceAction = objects.property<Action<Component>>()
    private val inference = objects.property<Inference>()

    fun kotlinComponents(action: Action<Component> = Action { }) {
        inference = KotlinInference()
        inferenceAction = action
    }

    fun gradlePluginComponents(action: Action<Component> = Action { }) {
        inference = GradlePluginInference()
        inferenceAction = action
    }

    // private val sourcesInjection = objects.property<String>()
    // private val docsInjection = objects.property<String>()

    internal fun fallback(to: Content) {
        // inherit.fallback(to.inherit) // doesn't seem right
        // sourcesInjection.fallback(to.sourcesInjection)
        // docsInjection.fallback(to.docsInjection)
        to.inheritedComponents.all { inheritedComponents.add(this) }
        to.inheritedComponents.whenObjectRemoved { inheritedComponents.remove(this) }
        to.declaredComponents.all { inheritedComponents.add(this) }
        to.declaredComponents.whenObjectRemoved { inheritedComponents.remove(this) }
    }

    /* fun emptySources() { sourcesInjection.set("empty") }
    fun emtpyDocs() { docsInjection.set("empty") }
    @Deprecated("autoDocs is deprecated.", level = DeprecationLevel.WARNING)
    fun autoDocs() { docsInjection.set("auto") }
    @Deprecated("autoSources is deprecated.", level = DeprecationLevel.WARNING)
    fun autoSources() { sourcesInjection.set("auto") } */

    internal fun resolve(project: Project, spec: DeploySpec) {
        inherit.finalizeValue() // fixes 'components'
        inference.finalizeValue()
        inferenceAction.finalizeValue()
        inference.orNull?.inferComponents(project, spec) { configure ->
            component {
                configure()
                inferenceAction.orNull?.execute(this)
            }
        }
        /* val commonSources = sourcesInjection.orNull
        val commonDocs = docsInjection.orNull
        allComponents.configureEach {
            when {
                sources.isPresent -> { /* already provided */ }
                commonSources == "empty" -> sources(project.makeSourcesJar(spec, this, true))
                commonSources == "auto" -> sources(project.makeSourcesJar(spec, this, false))
            }
            when {
                docs.isPresent -> { /* already provided */ }
                commonDocs == "empty" -> docs(project.makeDocsJar(spec, this, true))
                commonDocs == "auto" -> docs(project.makeDocsJar(spec, this, false))
            }
        } */
    }
}
