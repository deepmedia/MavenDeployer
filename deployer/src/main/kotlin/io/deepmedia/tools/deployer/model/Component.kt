package io.deepmedia.tools.deployer.model

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class Component @Inject constructor(objects: ObjectFactory) {

    internal sealed class Origin {
        class MavenPublication(val name: String, val clone: Boolean = false) : Origin()
        class SoftwareComponent(val name: String) : Origin()

        fun publicationName(spec: DeploySpec): String = when {
            this is SoftwareComponent -> name + "SoftwareFor" + spec.name.capitalize()
            this is MavenPublication && clone -> name + "ClonedFor" + spec.name.capitalize()
            else -> (this as MavenPublication).name
        }
    }

    internal val origin: Property<Origin> = objects.property()

    var inferred: Boolean = false
        internal set

    fun fromMavenPublication(name: String, clone: Boolean = false) {
        origin.set(Origin.MavenPublication(name, clone))
        configureWhen { block ->
            val publishing = extensions.getByType(PublishingExtension::class.java)
            publishing.publications.configureEach {
                if (this.name == name) block()
            }
        }
    }

    fun fromSoftwareComponent(name: String) {
        origin.set(Origin.SoftwareComponent(name))
        configureWhen { block ->
            components.configureEach {
                if (this.name == name) block()
            }
        }
    }

    internal val sources: Property<Any> = objects.property()
    internal val docs: Property<Any> = objects.property()
    fun sources(task: Any?) { sources.set(task) }
    fun docs(task: Any?) { docs.set(task) }

    val groupId = objects.property<Transformer<String, String>>()
    val artifactId = objects.property<Transformer<String, String>>()

    internal fun whenConfigurable(project: Project, block: () -> Unit) {
        require(::configureWhenBlock.isInitialized) {
            "Components must be initialized with fromMavenPublication() or fromSoftwareComponent()."
        }
        project.configureWhenBlock(block)
    }

    private lateinit var configureWhenBlock: Project.(configurationBlock: () -> Unit) -> Unit
    internal fun configureWhen(block: Project.(configurationBlock: () -> Unit) -> Unit) {
        configureWhenBlock = block
    }
}

interface ComponentScope {
    fun component(action: Action<Component>)

    fun softwareComponent(name: String, action: Action<Component> = Action {  }) = component {
        fromSoftwareComponent(name)
        action.execute(this)
    }

    fun mavenPublication(name: String, clone: Boolean = false, action: Action<Component> = Action {  }) = component {
        fromMavenPublication(name, clone)
        action.execute(this)
    }
}