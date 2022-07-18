package io.deepmedia.tools.deployer.model

import com.android.build.gradle.BaseExtension
import io.deepmedia.tools.deployer.*
import io.deepmedia.tools.deployer.isAndroidLibraryProject
import io.deepmedia.tools.deployer.isJavaProject
import io.deepmedia.tools.deployer.isKotlinProject
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import javax.inject.Inject

open class Component @Inject constructor(objects: ObjectFactory) {

    internal sealed class Origin {
        class MavenPublication(val name: String, val clone: Boolean = false) : Origin()
        class SoftwareComponent(val name: String) : Origin()
        fun publicationName(spec: DeploySpec): String = when (this) {
            is SoftwareComponent -> name + "SoftwareFor" + spec.name.capitalize()
            is MavenPublication -> name + "MavenFor" + spec.name.capitalize()
        }

        /* fun generateTaskName(what: String): String {
            val name = when (this) {
                is SoftwareComponent -> name
                is MavenPublication -> name
            }
            return "generate${name.capitalize()}${what.capitalize()}ForDeployment"
        } */
    }

    internal val origin: Property<Origin> = objects.property()

    fun fromMavenPublication(name: String, clone: Boolean = false) {
        origin.set(Origin.MavenPublication(name, clone))
    }
    fun fromSoftwareComponent(name: String) {
        origin.set(Origin.SoftwareComponent(name))
    }

    internal val sources: Property<Any> = objects.property()
    internal val docs: Property<Any> = objects.property()
    fun sources(task: Any?) { sources.set(task) }
    fun docs(task: Any?) { docs.set(task) }

    val groupId = objects.property<Transformer<String, String>>()
    val artifactId = objects.property<Transformer<String, String>>()

    /*
    internal lateinit var resolvedSources: Provider<Any>
    internal lateinit var resolvedDocs: Provider<Any>
    val autoSources = objects.property<Boolean>().convention(false)
    val autoDocs = objects.property<Boolean>().convention(false)
    fun autoSources(auto: Boolean = true) { autoSources.set(auto) }
    fun autoDocs(auto: Boolean = true) { autoDocs.set(auto) } */

    /* internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        resolvedDocs = docs.orElse(target.provider {
            if (!autoDocs.get()) return@provider null
            if (!target.isKotlinProject) return@provider null
            val taskName = resolvedOrigin.get().generateTaskName("docs")
            runCatching { target.tasks.named(taskName) }.getOrElse {
                target.apply<DokkaPlugin>()
                target.tasks.register(taskName, Jar::class.java) {
                    val dokkaTask = when {
                        target.isKotlinMultiplatformProject -> "dokkaHtml"
                        else -> "dokkaJavadoc"
                    }.let { target.tasks[it] as DokkaTask }
                    dependsOn(dokkaTask)
                    archiveClassifier.set("javadoc")
                    from(dokkaTask.outputDirectory)
                }
            }
        })
        resolvedSources = sources.orElse(target.provider {
            if (!autoSources.get()) return@provider null
            if (!target.isJavaProject) return@provider null
            val taskName = resolvedOrigin.get().generateTaskName("sources")
            runCatching { target.tasks.named(taskName) }.getOrElse {
                target.tasks.register(taskName, Jar::class.java) {
                    archiveClassifier.set("sources")
                    if (target.isAndroidLibraryProject) {
                        val android = project.extensions["android"] as BaseExtension
                        from(android.sourceSets["main"].java.srcDirs)
                    } else {
                        val java: JavaPluginExtension = project.extensions.getByType()
                        val sourceSet = java.sourceSets["main"]
                        from(sourceSet.allSource)
                    }
                }
            }
        })
    } */
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