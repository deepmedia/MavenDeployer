package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.*
import io.deepmedia.tools.deployer.tasks.makeDocsJar
import io.deepmedia.tools.deployer.tasks.makeSourcesJar
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets
import org.jetbrains.kotlin.gradle.targets.native.tasks.artifact.kotlinArtifactsExtension
import org.w3c.dom.Element
import javax.inject.Inject

open class Content @Inject constructor(private val objects: ObjectFactory) : ComponentScope {

    val infer = objects.property<Boolean>()

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

    internal fun resolve(project: Project, spec: DeploySpec) {
        val commonSources = sourcesInjection.orNull
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
        }
    }
}
