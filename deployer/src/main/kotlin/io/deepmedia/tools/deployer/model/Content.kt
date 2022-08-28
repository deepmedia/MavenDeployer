package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.*
import io.deepmedia.tools.deployer.tasks.makeAutoDocsJar
import io.deepmedia.tools.deployer.tasks.makeAutoSourcesJar
import io.deepmedia.tools.deployer.tasks.makeEmptyDocsJar
import io.deepmedia.tools.deployer.tasks.makeEmptySourcesJar
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
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

    private val sourcesInjection = objects.property<String>()
    private val docsInjection = objects.property<String>()

    internal fun fallback(to: Content) {
        infer.fallback(to.infer)
        sourcesInjection.fallback(to.sourcesInjection)
        docsInjection.fallback(to.docsInjection)
        to.allComponents.all { allComponents.add(this) }
        to.allComponents.whenObjectRemoved { allComponents.remove(this) }
    }

    fun emptySources() { sourcesInjection.set("empty") }
    fun emtpyDocs() { docsInjection.set("empty") }
    fun autoDocs() { docsInjection.set("auto") }
    fun autoSources() { sourcesInjection.set("auto") }

    @Suppress("UNUSED_PARAMETER")
    internal fun resolve(project: Project, spec: DeploySpec) {
        val sourcesInjection = sourcesInjection.orNull
        val docsInjection = docsInjection.orNull
        allComponents.configureEach {
            when (sourcesInjection) {
                "empty" -> sources(project.makeEmptySourcesJar)
                "auto" -> sources(project.makeAutoSourcesJar)
                else -> { /* Not supported */ }
            }
            when (docsInjection) {
                "empty" -> docs(project.makeEmptyDocsJar)
                "auto" -> docs(project.makeAutoDocsJar)
                else -> { /* Not supported */ }
            }
        }
    }

    companion object {
        internal fun inferred(project: Project): DomainObjectSet<Component> {
            val set = project.objects.domainObjectSet(Component::class)
            fun component(configure: Component.() -> Unit) {
                val comp: Component = project.objects.newInstance()
                comp.configure()
                comp.inferred = true
                set.add(comp)
            }
            project.afterEvaluate {
                when {
                    // Kotlin multiplatform projects have one artifact per target, plus one for metadata.
                    project.isKmpProject -> {
                        val kotlin = project.kotlinExtension as KotlinMultiplatformExtension
                        kotlin.targets.all {
                            component { fromKotlinTarget(this@all, clone = true) }
                        }
                    }
                    // When java-gradle-plugin is applied and isAutomatedPublishing is true, X+1 publications
                    // are created. First is "pluginMaven" with the artifact, and the rest are plugin markers
                    // called "<PLUGIN_NAME>PluginMarkerMaven". Markers have no jar, just pom file.
                    project.isGradlePluginProject -> run {
                        val gradlePlugin = project.extensions.getByType<GradlePluginDevelopmentExtension>()
                        if (!gradlePlugin.isAutomatedPublishing) return@run
                        component {
                            fromMavenPublication("pluginMaven", clone = true)
                        }
                        gradlePlugin.plugins.all {
                            component {
                                fromGradlePluginDeclaration(this@all, clone = true)
                            }
                        }
                    }
                    // TODO: starting from 7.1.0, user has many options. we should read from AGP extension.
                    // https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/build-system/gradle-api/src/main/java/com/android/build/api/dsl/LibraryPublishing.kt
                    project.isAndroidLibraryProject -> component { fromSoftwareComponent("release") }
                    project.isKotlinProject -> component { fromSoftwareComponent("kotlin") }
                    project.isJavaProject -> component { fromSoftwareComponent("java") }
                }
            }
            return set
        }
    }
}
