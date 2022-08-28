package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.isKmpProject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.property
import org.gradle.plugin.devel.PluginDeclaration
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import javax.inject.Inject

open class Component @Inject constructor(objects: ObjectFactory) {

    internal sealed class Origin(val tag: Any?) {
        class MavenPublication(val name: String, val clone: Boolean = false, tag: Any? = null) : Origin(tag)
        class SoftwareComponent(val name: String, tag: Any? = null) : Origin(tag)
    }

    internal val origin: Property<Origin> = objects.property()

    internal fun maybeCreatePublication(publications: PublicationContainer, spec: DeploySpec): MavenPublication {
        val origin = origin.get()
        val name = when {
            origin is Origin.SoftwareComponent -> origin.name + "SoftwareFor" + spec.name.capitalize()
            origin is Origin.MavenPublication && origin.clone -> origin.name + "ClonedFor" + spec.name.capitalize()
            else -> (origin as Origin.MavenPublication).name
        }
        return when {
            origin is Origin.SoftwareComponent -> publications.create(name, MavenPublication::class)
            origin is Origin.MavenPublication && origin.clone -> publications.create(name, MavenPublication::class)
            else -> publications[name] as MavenPublication
        }
    }

    val tag: Any? get() = origin.orNull?.tag

    var inferred: Boolean = false
        internal set

    fun fromMavenPublication(name: String, clone: Boolean = false, tag: Any? = null) {
        origin.set(Origin.MavenPublication(name, clone, tag))
        configureWhen { block ->
            val publishing = extensions.getByType(PublishingExtension::class.java)
            publishing.publications.configureEach {
                if (this.name == name) block()
            }
        }
    }

    fun fromSoftwareComponent(name: String, tag: Any? = null) {
        origin.set(Origin.SoftwareComponent(name, tag))
        configureWhen { block ->
            components.configureEach {
                if (this.name == name) block()
            }
        }
    }

    fun fromKotlinTarget(target: KotlinTarget, clone: Boolean = false) {
        if (!target.project.isKmpProject) {
            // TODO: test this
            fromSoftwareComponent("kotlin", tag = target)
        } else if (target.platformType == KotlinPlatformType.common) {
            fromMavenPublication("kotlinMultiplatform", clone = clone, tag = target)
            // for metadata publication, mavenPublication is not called. But luckily when it is added it
            // already has the component so no extra configureWhen is needed here. See:
            // https://youtrack.jetbrains.com/issue/KT-53300
        } else {
            fromMavenPublication(target.name, clone = clone, tag = target)
            artifactId.set { "$it-${target.name.lowercase()}" }
            // https://github.com/JetBrains/kotlin/blob/v1.7.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/Publishing.kt#L83
            // Need to configure not only when the publication is created, but also after it is configured
            // with a software component, which in Kotlin Gradle Plugin happens in afterEvaluate after
            // dispatching the mavenPublication blocks. If we don't take care of this detail, publications
            // can fail and/or not include gradle module metadata.
            configureWhen { block ->
                target.mavenPublication {
                    afterEvaluate { block() }
                }
            }
        }
    }

    fun fromGradlePluginDeclaration(declaration: PluginDeclaration, clone: Boolean = false) {
        fromMavenPublication("${declaration.name}PluginMarkerMaven", clone = clone, tag = declaration)
        groupId.set { declaration.id }
        artifactId.set { declaration.id + ".gradle.plugin" }
    }

    internal val sources: Property<Any> = objects.property()
    internal val docs: Property<Any> = objects.property()
    fun sources(task: Any?) { sources.set(task) }
    fun docs(task: Any?) { docs.set(task) }

    val extras = objects.domainObjectSet(Any::class)

    val groupId = objects.property<Transformer<String, String>>()
    val artifactId = objects.property<Transformer<String, String>>()

    val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)

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

@Suppress("unused")
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

    fun kotlinTarget(target: KotlinTarget, clone: Boolean = false, action: Action<Component> = Action {  }) = component {
        fromKotlinTarget(target, clone)
        action.execute(this)
    }

    fun gradlePluginDeclaration(declaration: PluginDeclaration, clone: Boolean = false, action: Action<Component> = Action {  }) = component {
        fromGradlePluginDeclaration(declaration, clone)
        action.execute(this)
    }
}