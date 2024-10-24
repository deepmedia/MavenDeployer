package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.inference.AndroidInference
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

    private data class InferenceData(val inference: Inference, val action: Action<Component>)
    private val inferenceData = objects.listProperty<InferenceData>()

    fun kotlinComponents(action: Action<Component> = Action { }) {
        inferenceData.add(InferenceData(KotlinInference(), action))
    }

    fun gradlePluginComponents(action: Action<Component> = Action { }) {
        inferenceData.add(InferenceData(GradlePluginInference(), action))
    }

    fun androidComponents(componentName: String, vararg otherComponentNames: String, action: Action<Component> = Action { }) {
        inferenceData.add(InferenceData(AndroidInference(listOf(componentName, *otherComponentNames)), action))
    }

    internal fun fallback(to: Content) {
        to.inheritedComponents.all { inheritedComponents.add(this) }
        to.inheritedComponents.whenObjectRemoved { inheritedComponents.remove(this) }
        to.declaredComponents.all { inheritedComponents.add(this) }
        to.declaredComponents.whenObjectRemoved { inheritedComponents.remove(this) }
    }

    internal fun resolve(project: Project, spec: DeploySpec) {
        inherit.finalizeValue() // fixes 'components'
        inferenceData.finalizeValue()
        inferenceData.get().forEach {
            it.inference.inferComponents(project, spec) { configure ->
                component {
                    configure()
                    it.action.execute(this)
                }
            }
        }
    }
}
