package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.*
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import javax.inject.Inject

open class Content @Inject constructor(private val objects: ObjectFactory) : ComponentScope {

    val infer = objects.property<Boolean>()
    fun infer(infer: Boolean) { this.infer.set(infer) }

    val components = objects.domainObjectSet(Component::class)
    internal lateinit var resolvedComponents: Provider<List<Component>>

    override fun component(action: Action<Component>) {
        val component: Component = objects.newInstance()
        action.execute(component)
        components.add(component)
    }

    internal fun fallback(to: Content) {
        infer.fallback(to.infer)
        to.components.whenObjectAdded { components.add(this) }
        to.components.whenObjectRemoved { components.remove(this) }
    }

    internal fun resolve(project: Project, spec: DeploySpec) {
        val inferred = inferredComponents.getOrPut(project) {
            val set = project.objects.domainObjectSet(Component::class)
            fun component(configure: Component.() -> Unit) {
                val comp: Component = project.objects.newInstance()
                comp.configure()
                set.add(comp)
            }
            when {
                project.isKotlinMultiplatformProject -> {
                    // Kotlin multiplatform projects have one artifact per target, plus one for metadata.
                    val kotlin = project.kotlinExtension as KotlinMultiplatformExtension
                    kotlin.targets.all {
                        val target = this
                        component {
                            val isMetadata = target.platformType == KotlinPlatformType.common
                            fromMavenPublication(if (isMetadata) "kotlinMultiplatform" else target.name, clone = true)
                            if (!isMetadata) {
                                artifactId.set { "$it-${target.name.toLowerCase()}"}
                            }
                        }
                    }
                }
                project.isGradlePluginProject -> {
                    // When java-gradle-plugin is applied and isAutomatedPublishing is true, X+1 publications
                    // are created. First is "pluginMaven" with the artifact, and the rest are plugin markers
                    // called "<PLUGIN_NAME>PluginMarkerMaven". Markers have no jar, just pom file.
                    val gradlePlugin = project.extensions.getByType<GradlePluginDevelopmentExtension>()
                    if (gradlePlugin.isAutomatedPublishing) {
                        component {
                            fromMavenPublication("pluginMaven", clone = true)
                        }
                        gradlePlugin.plugins.all {
                            val plugin = this
                            component {
                                fromMavenPublication("${plugin.name}PluginMarkerMaven", clone = true)
                                groupId.set { plugin.id }
                                artifactId.set { plugin.id + ".gradle.plugin" }
                            }
                        }
                    }
                }
                project.isAndroidLibraryProject -> {
                    component { fromSoftwareComponent("release") }
                }
                project.isJavaProject -> {
                    component { fromSoftwareComponent("java") }
                }
            }
            set
        }

        resolvedComponents = project.provider {
            if (infer.getOrElse(true)) {
                inferred.all { components.add(this) }
            }
            components.toList()
        }
    }

    companion object {
        private val inferredComponents = mutableMapOf<Project, DomainObjectSet<Component>>()
    }
}
