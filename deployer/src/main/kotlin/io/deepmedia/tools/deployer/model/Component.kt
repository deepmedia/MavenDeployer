package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.*
import io.deepmedia.tools.deployer.isAndroidLibraryProject
import io.deepmedia.tools.deployer.isGradlePluginProject
import io.deepmedia.tools.deployer.isKmpProject
import io.deepmedia.tools.deployer.isKotlinProject
import org.gradle.api.*
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets
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
        is Origin.SoftwareComponent -> "soft:${o.component.name}"
        is Origin.SoftwareComponentName -> "softn:${o.name}"
        is Origin.MavenPublicationName -> "pub:${o.name}"
        is Origin.ArtifactSet -> "art:${o.id}"
    }

    internal fun maybeCreatePublication(publications: PublicationContainer, spec: DeploySpec): MavenPublication {
        val origin = origin.get()
        val name = when (origin) {
            is Origin.SoftwareComponent -> origin.component.name + "SoftwareFor" + spec.name.capitalize()
            is Origin.SoftwareComponentName -> origin.name + "SoftwareFor" + spec.name.capitalize()
            is Origin.MavenPublicationName -> when {
                origin.clone -> origin.name + "ClonedFor" + spec.name.capitalize()
                else -> origin.name
            }
            is Origin.ArtifactSet -> "artifacts" + origin.id + "For" + spec.name.capitalize()
        }
        val existing = publications.findByName(name)
        return when {
            existing != null -> existing as MavenPublication
            origin is Origin.MavenPublicationName && !origin.clone -> error("Source publication ${origin.name} not found.")
            else -> publications.create(name, MavenPublication::class)
        }
    }

    val tag: Any? get() = origin.orNull?.tag

    var inferred: Boolean = false
        internal set

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
     */
    fun fromKotlinTarget(target: KotlinTarget) {
        if (target.platformType == KotlinPlatformType.common) {
            fromMavenPublication("kotlinMultiplatform", clone = true, tag = target)
            // for metadata publication, mavenPublication is not called. But luckily when it is added it
            // already has the component so no extra configureWhen is needed here. See:
            // https://youtrack.jetbrains.com/issue/KT-53300
        } else if (target.platformType == KotlinPlatformType.androidJvm) {
            error("fromKotlinTarget can't be used with Android targets. Use fromSoftwareComponent instead.")
        } else {
            val softwareComponent = target.components.singleOrNull()
            softwareComponent ?: error("$target has more/less than 1 component: ${target.components}")
            fromSoftwareComponent(softwareComponent, target)
            if (target.project.isKmpProject) {
                artifactId.set { "$it-${target.name.lowercase()}" }
            }

            // OLD, PUBLICATION BASED IMPL
            // fromMavenPublication(target.name, clone = clone, tag = target)
            // https://github.com/JetBrains/kotlin/blob/v1.7.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/Publishing.kt#L83
            // Need to configure not only when the publication is created, but also after it is configured
            // with a software component, which in Kotlin Gradle Plugin happens in afterEvaluate after
            // dispatching the mavenPublication blocks. If we don't take care of this detail, publications
            // can fail and/or not include gradle module metadata.
            /* configureWhen { block ->
                target.mavenPublication {
                    afterEvaluate { block() }
                }
            } */
        }
    }

    fun fromGradlePluginDeclaration(declaration: PluginDeclaration, clone: Boolean = false) {
        fromMavenPublication("${declaration.name}PluginMarkerMaven", clone = clone, tag = declaration)
        groupId.set { declaration.id }
        artifactId.set { declaration.id + ".gradle.plugin" }
        isMarker.set(true) // markers should not have docs/jars, see `isMarker` comments
    }

    fun fromArtifactSet(artifacts: Action<Artifacts>) {
        val container: Artifacts = objects.newInstance()
        artifacts.execute(container)
        origin.set(Origin.ArtifactSet(container))
        configureWhen { block -> block() }
    }

    internal val sources: Property<Any> = objects.property()
    internal val docs: Property<Any> = objects.property()
    fun sources(task: Any?) { sources.set(task) }
    fun docs(task: Any?) { docs.set(task) }

    val extras: Artifacts = objects.newInstance()

    val groupId = objects.property<Transformer<String, String>>()
    val artifactId = objects.property<Transformer<String, String>>()

    val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)

    // Whether this is a fake/marker/wrapper component that should NOT have autodocs and autosources
    // Ideally we should infer this from the component origin using some special logic, but for now
    // we use this flag to fix some signing conflict when publishing gradle plugins.
    // (both marker publication and main publication produce the very same javadoc jar, and they both sign it
    //  on the same file location. Gradle gets confused and notices a missing cross-dependency between the two pubs)
    internal val isMarker: Property<Boolean> = objects.property<Boolean>().convention(false)

    internal fun whenConfigurable(project: Project, block: () -> Unit) {
        require(::configureWhenBlock.isInitialized) {
            "Components must be initialized with one of the from*() functions."
        }
        project.configureWhenBlock(block)
    }

    private lateinit var configureWhenBlock: Project.(configurationBlock: () -> Unit) -> Unit
    internal fun configureWhen(block: Project.(configurationBlock: () -> Unit) -> Unit) {
        configureWhenBlock = block
    }

    internal var xml: ((XmlProvider, (Component) -> MavenPublication) -> Unit)? = null
}

@Suppress("unused")
interface ComponentScope {
    fun component(action: Action<Component>)
}

internal fun inferredComponents(project: Project): DomainObjectSet<Component> {
    val set = project.objects.domainObjectSet(Component::class)

    fun inferredComponent(configure: Component.() -> Unit): Component {
        val comp: Component = project.objects.newInstance()
        comp.configure()
        comp.inferred = true
        set.add(comp)
        return comp
    }

    // Wait for evaluation so that we can be sure of the presence of other plugins.
    // We need to know which plugins are present and which aren't to make the best decision.
    project.whenEvaluated {
        when {
            // KMP:
            // Kotlin multiplatform projects have one artifact per target, plus one for metadata
            // which is also exposed as a target with common platform.
            project.isKmpProject -> {
                val kotlin = project.kotlinExtension as KotlinMultiplatformExtension
                kotlin.targets.all {
                    val target = this
                    if (target.platformType != KotlinPlatformType.androidJvm) {
                        inferredComponent { fromKotlinTarget(this@all) }
                    } else {
                        // Android's KotlinTarget.components must be called in afterEvaluate,
                        // possibly nested so we jump after AGP's afterEvaluate blocks.
                        // There may be 0, 1 or more components depending on how KotlinAndroidTarget was configured.
                        // NOTE: if multiple components are present, they will have the same artifactId.
                        project.whenEvaluated {
                            target.components.forEach {
                                inferredComponent {
                                    fromSoftwareComponent(it, tag = this@all)
                                    artifactId.set { "$it-${target.name.lowercase()}" }
                                }
                            }
                        }
                    }
                }
            }

            // GRADLE PLUGINS:
            // When java-gradle-plugin is applied and isAutomatedPublishing is true, X+1 publications
            // are created. First is "pluginMaven" with the artifact, and the rest are plugin markers
            // called "<PLUGIN_NAME>PluginMarkerMaven". Markers have no jar, just pom file with single dep.
            // We use xml action to rewrite the dependency in the markers, because the main component coordinates
            // might be overridden by the user while Gradle refers to the original pluginMaven publication which we clone.
            // See MavenPluginPublishPlugin.java::createMavenMarkerPublication()
            project.isGradlePluginProject -> run {
                val gradlePlugin = project.extensions.getByType<GradlePluginDevelopmentExtension>()
                if (!gradlePlugin.isAutomatedPublishing) return@run
                val mainComponent = inferredComponent {
                    fromMavenPublication("pluginMaven", clone = true)
                    packaging.set("jar")
                }
                gradlePlugin.plugins.all {
                    inferredComponent {
                        fromGradlePluginDeclaration(this@all, clone = true)
                        xml = { xml, publicationOf ->
                            val mainPublication = publicationOf(mainComponent)
                            val root: Element = xml.asElement()
                            val oldDependencies = (0 until root.childNodes.length).map { root.childNodes.item(it) }.firstOrNull { it.nodeName == "dependencies" }
                            if (oldDependencies != null) {
                                root.removeChild(oldDependencies)
                            }
                            val dependencies = root.appendChild(root.ownerDocument.createElement("dependencies"))
                            val dependency = dependencies.appendChild(root.ownerDocument.createElement("dependency"))
                            val groupId = dependency.appendChild(root.ownerDocument.createElement("groupId"))
                            groupId.textContent = mainPublication.groupId
                            val artifactId = dependency.appendChild(root.ownerDocument.createElement("artifactId"))
                            artifactId.textContent = mainPublication.artifactId
                            val version = dependency.appendChild(root.ownerDocument.createElement("version"))
                            version.textContent = mainPublication.version
                        }
                    }
                }
            }

            // TODO: starting from AGP 7.1.0, user must explicitly decide which components will be created in the
            //  publishing DSL block. He will pick variants (or choose a custom component name) and a software component
            //  with that name will be created. But there doesn't seem to be any way of reading variants from AGP plugin
            // https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/build-system/gradle-api/src/main/java/com/android/build/api/dsl/LibraryPublishing.kt
            project.isAndroidLibraryProject -> inferredComponent {
                fromSoftwareComponent("release")
                packaging.set("aar")
            }

            project.isKotlinProject -> {
                val kotlin = project.kotlinExtension as KotlinSingleTargetExtension<*>
                inferredComponent { fromKotlinTarget(kotlin.target) }
            }

            project.isJavaProject -> inferredComponent { fromSoftwareComponent("java") }
        }
    }
    return set
}
