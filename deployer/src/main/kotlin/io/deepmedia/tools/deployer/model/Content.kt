package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.*
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import javax.inject.Inject

open class Content @Inject constructor(private val objects: ObjectFactory) : ComponentScope {

    val infer = objects.property<Boolean>()
    fun infer(infer: Boolean) { this.infer.set(infer) }

    val allComponents = objects.domainObjectSet(Component::class)
    val components = allComponents.matching {
        it.inferred == infer.getOrElse(true)
    }

    override fun component(action: Action<Component>) {
        val component: Component = objects.newInstance()
        action.execute(component)
        allComponents.add(component)
    }

    internal fun fallback(to: Content) {
        infer.fallback(to.infer)
        to.allComponents.all {
            allComponents.add(this)
        }
        to.allComponents.whenObjectRemoved {
            allComponents.remove(this)
        }
    }

    internal fun resolve(project: Project, spec: DeploySpec) {
        val inferred = inferredComponents(project)
        inferred.all { allComponents.add(this) }
        inferred.whenObjectRemoved { allComponents.remove(this) }
    }

    companion object {
        // TODO: could save to project.extensions
        private val inferredComponents = mutableMapOf<Project, DomainObjectSet<Component>>()

        private fun inferredComponents(project: Project) = inferredComponents.getOrPut(project) {
            val set = project.objects.domainObjectSet(Component::class)
            fun component(configure: Component.() -> Unit) {
                val comp: Component = project.objects.newInstance()
                comp.configure()
                comp.inferred = true
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
                                artifactId.set { "$it-${target.name.toLowerCase()}" }
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
                            } else {
                                // metadata publication, mavenPublication is not called. But luckily when it is added it
                                // already has the component so no extra configureWhen is needed here. See:
                                // https://youtrack.jetbrains.com/issue/KT-53300
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
                            // These help with sonatype publications
                            // TODO: add them in validateArtifacts so only for sonatype and only if missing
                            sources(project.tasks.makeEmptySourcesJar)
                            docs(project.tasks.makeEmptyJavadocJar)
                        }
                        gradlePlugin.plugins.all {
                            val plugin = this
                            component {
                                fromMavenPublication("${plugin.name}PluginMarkerMaven", clone = true)
                                groupId.set { plugin.id }
                                artifactId.set { plugin.id + ".gradle.plugin" }
                                // These help with sonatype publications
                                // TODO: add them in validateArtifacts so only for sonatype and only if missing
                                sources(project.tasks.makeEmptySourcesJar)
                                docs(project.tasks.makeEmptyJavadocJar)
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
    }
}
