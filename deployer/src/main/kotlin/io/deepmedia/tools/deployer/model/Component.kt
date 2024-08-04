package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.capitalized
import io.deepmedia.tools.deployer.tasks.*
import io.deepmedia.tools.deployer.tasks.makeEmptyDocsJar
import io.deepmedia.tools.deployer.tasks.makeEmptySourcesJar
import org.gradle.api.*
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.PluginDeclaration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.w3c.dom.Element
import javax.inject.Inject

open class Component @Inject constructor(private val objects: ObjectFactory) {

    internal sealed class Origin(val tag: Any?) {
        class MavenPublicationName(val name: String, val clone: Boolean = false, tag: Any? = null) : Origin(tag)
        class SoftwareComponent(val component: org.gradle.api.component.SoftwareComponent, tag: Any? = null) : Origin(tag)
        class SoftwareComponentName(val name: String, tag: Any? = null) : Origin(tag)
        class ArtifactSet(val artifacts: Artifacts) : Origin(null) {
            val id = ids++
            companion object {
                var ids = 0
            }
        }
    }

    internal val origin: Property<Origin> = objects.property()

    internal val shortName: String get() = when (val o = origin.get()) {
        is Origin.SoftwareComponent -> "${o.component.name}Component"
        is Origin.SoftwareComponentName -> "${o.name}Component"
        is Origin.MavenPublicationName -> "${o.name}Publication"
        is Origin.ArtifactSet -> "artifacts${o.id}"
    }

    internal fun maybeCreatePublication(publications: PublicationContainer, spec: DeploySpec): MavenPublication {
        val origin = origin.get()
        val name = when (origin) {
            is Origin.SoftwareComponent -> spec.name + origin.component.name.capitalized() + "Component"
            is Origin.SoftwareComponentName -> spec.name + origin.name.capitalized() + "Component"
            is Origin.MavenPublicationName -> when {
                origin.clone -> spec.name + origin.name.capitalized() + "Clone"
                else -> origin.name
            }
            is Origin.ArtifactSet -> spec.name + "Artifacts" + origin.id
        }
        val existing = publications.findByName(name)
        return when {
            existing != null -> existing as MavenPublication
            origin is Origin.MavenPublicationName && !origin.clone -> error("Source publication ${origin.name} not found.")
            else -> publications.create(name, MavenPublication::class)
        }
    }

    val tag: Any? get() = origin.orNull?.tag

    // var inferred: Boolean = false
    //     internal set

    val packaging: Property<String> = objects.property()

    fun fromMavenPublication(name: String, clone: Boolean = false, tag: Any? = null) {
        origin.set(Origin.MavenPublicationName(name, clone, tag))
        configureWhen { block ->
            val publishing = extensions.getByType(PublishingExtension::class.java)
            publishing.publications.configureEach {
                if (this.name == name) block()
            }
        }
    }

    fun fromJava() {
        fromSoftwareComponent("java")
    }

    fun fromSoftwareComponent(name: String, tag: Any? = null) {
        origin.set(Origin.SoftwareComponentName(name, tag))
        configureWhen { block ->
            components.configureEach {
                if (this.name == name) block()
            }
        }
    }

    fun fromSoftwareComponent(component: SoftwareComponent, tag: Any? = null) {
        origin.set(Origin.SoftwareComponent(component, tag))
        configureWhen { block -> block() }
    }

    /**
     * NOTE: Doesn't work well for [KotlinAndroidTarget]s.
     * Android targets can be configured for publishing in two different ways:
     * - via AGP publishing block. In that case, user should know the variants he configured to be
     *   publishable, and a software component with that name should be retrieved when possible.
     * - via KGP's [KotlinMultiplatformExtension.androidTarget] configuration block.
     *   Then the software components can be retrieved using [KotlinTarget.components] as we do below,
     *   but there are a few issues that make this function unsafe:
     *   - there might be no components, if [KotlinMultiplatformExtension.androidTarget] was not configured
     *   - there might be more than one component
     *   - [KotlinTarget.components] *must* be called in a afterEvaluate block, after AGP's afterEvaluates
     *
     * Using [KotlinOnlyTarget] so that [KotlinAndroidTarget] is automatically excluded.
     */
    fun fromKotlinTarget(target: KotlinOnlyTarget<*>) {
        if (target is KotlinMetadataTarget) {
            fromMavenPublication("kotlinMultiplatform", clone = true, tag = target)
            // for metadata publication, target.mavenPublication is not called. But luckily when it is added it
            // already has the component so no extra configureWhen is needed here. See:
            // https://youtrack.jetbrains.com/issue/KT-53300
        } else {
            fromNonAndroidNonMetadataKotlinTarget(target)
        }
    }

    fun fromKotlinTarget(target: KotlinWithJavaTarget<*, *>) {
        fromNonAndroidNonMetadataKotlinTarget(target)
    }

    private fun fromNonAndroidNonMetadataKotlinTarget(target: KotlinTarget) {
        val softwareComponents = target.components
        val softwareComponent = softwareComponents.singleOrNull()
        softwareComponent ?: error("$target has more/less than 1 component: ${softwareComponents}")
        fromSoftwareComponent(softwareComponent, target)
    }

    /**
     * When `java-gradle-plugin` is applied and `isAutomatedPublishing` is true, X+1 publications are created:
     * - a main publication called "pluginMaven" which will contain a JAR with code for all declared plugins
     * - X marker publications called "${pluginName}PluginMarkerMaven". These have no JAR, they're just a pom file
     *   with a single dependency.
     *
     * The function below can be used to make this component represent one of the marker artifacts.
     * See MavenPluginPublishPlugin.createMavenMarkerPublication.
     *
     * Note: it is recommended to pass [mainComponent] as a reference to the main package containing all gradle plugins,
     * which [PluginDeclaration] belongs to. This way, the marker artifact will have the correct dependency coordinates
     * in case they were modified with respect to what [PluginDeclaration] uses by default.
     */
    fun fromGradlePluginDeclaration(declaration: PluginDeclaration, mainComponent: Component? = null, clone: Boolean = false) {
        fromMavenPublication("${declaration.name}PluginMarkerMaven", clone = clone, tag = declaration)
        groupId.set { declaration.id }
        artifactId.set { declaration.id + ".gradle.plugin" }

        // Markers have no sources/docs. Add empty jars to ease sonatype publishing.
        emptyDocs()
        emptySources()

        if (mainComponent != null) {
            xml = { xml, spec, publications ->
                val mainPublication = mainComponent.maybeCreatePublication(publications, spec)
                val root: Element = xml.asElement()
                val oldDependencies = (0 until root.childNodes.length).map { root.childNodes.item(it) }.firstOrNull { it.nodeName == "dependencies" }
                if (oldDependencies != null) {
                    root.removeChild(oldDependencies)
                }
                fun makeNode(name: String) = root.ownerDocument.createElement(name)
                root
                    .appendChild(makeNode("dependencies"))
                    .appendChild(makeNode("dependency"))
                    .apply {
                        appendChild(makeNode("groupId")).textContent = mainPublication.groupId
                        appendChild(makeNode("artifactId")).textContent = mainPublication.artifactId
                        appendChild(makeNode("version")).textContent = mainPublication.version
                    }
            }
        }
    }

    fun fromArtifactSet(artifacts: Action<Artifacts>) {
        val container: Artifacts = objects.newInstance()
        artifacts.execute(container)
        origin.set(Origin.ArtifactSet(container))
        configureWhen { block -> block() }
    }

    internal val sources: Property<Artifacts.Entry> = objects.property()
    internal val docs: Property<Artifacts.Entry> = objects.property()

    fun sources(artifact: Any, classifier: String = "sources", extension: String = "jar", builtBy: Any? = null) {
        sources.set(Artifacts.Entry.Dictionary(artifact, classifier, extension, builtBy))
    }

    fun emptySources() {
        sources.set(Artifacts.Entry.Promise { it.makeEmptySourcesJar })
    }

    fun kotlinSources() {
        sources.set(Artifacts.Entry.Promise { it.makeKotlinSourcesJar })
    }

    fun javaSources() {
        sources.set(Artifacts.Entry.Promise { it.makeJavaSourcesJar })
    }

    fun docs(artifact: Any, classifier: String = "javadoc", extension: String = "jar", builtBy: Any? = null) {
        docs.set(Artifacts.Entry.Dictionary(artifact, classifier, extension, builtBy))
    }

    fun emptyDocs() {
        docs.set(Artifacts.Entry.Promise { it.makeEmptyDocsJar })
    }

    // private val fallbackSources: Property<Project.() -> TaskProvider<Jar>> = objects.property()
    // private val fallbackDocs: Property<Project.() -> TaskProvider<Jar>> = objects.property()
    // fun emptySources() { fallbackSources.set { makeEmptySourcesJar } }
    // fun kotlinSources() { fallbackSources.set { makeKotlinSourcesJar } }
    // fun javaSources() { fallbackSources.set { makeJavaSourcesJar } }
    // fun emptyDocs() { fallbackDocs.set { makeEmptyDocsJar } }
    /* internal fun resolveSources(project: Project): Artifacts.Entry? {
        val fallback = fallbackSources.map { project.it() }
            .map { taskProvider -> Artifacts.Entry(taskProvider, taskProvider) }
        // val fallback = fallbackSources.map { project.makeSourcesJar(spec, this, project.it()) }
        return sources.orElse(fallback).orNull
    }

    internal fun resolveDocs(project: Project): Artifacts.Entry? {
        val fallback = fallbackSources.map { project.it() }
            .map { taskProvider -> Artifacts.Entry(taskProvider, taskProvider) }
        // val fallback = fallbackDocs.map { project.makeDocsJar(spec, this, project.it()) }
        return docs.orElse(fallback).orNull
    } */

    val extras: Artifacts = objects.newInstance()

    val groupId = objects.property<Transformer<String, String>>()
    val artifactId = objects.property<Transformer<String, String>>()

    val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)

    // Whether this is a fake/marker/wrapper component that should NOT have autodocs and autosources
    // Ideally we should infer this from the component origin using some special logic, but for now
    // we use this flag to fix some signing conflict when publishing gradle plugins.
    // (both marker publication and main publication produce the very same javadoc jar, and they both sign it
    //  on the same file location. Gradle gets confused and notices a missing cross-dependency between the two pubs)
    // internal val isMarker: Property<Boolean> = objects.property<Boolean>().convention(false)

    internal fun whenConfigurable(project: Project, block: () -> Unit) {
        require(::configureWhenBlock.isInitialized) {
            "Components must be initialized with one of the from*() functions."
        }
        project.configureWhenBlock(block)
    }

    private lateinit var configureWhenBlock: Project.(configurationBlock: () -> Unit) -> Unit
    private fun configureWhen(block: Project.(configurationBlock: () -> Unit) -> Unit) {
        configureWhenBlock = block
    }

    internal var xml: ((XmlProvider, AbstractDeploySpec<*>, PublicationContainer) -> Unit)? = null
}

@Suppress("unused")
interface ComponentScope {
    fun component(action: Action<Component>): Component
}

