package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Artifacts
import io.deepmedia.tools.deployer.model.Component
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.kotlin.dsl.get

private fun MavenPublication.addArtifacts(log: Logger, artifacts: Artifacts) {
    artifacts.entries.configureEach {
        log { "configureArtifacts: adding ${this.artifact} to MavenPublication ${name}" }
        val res = artifact(artifact)
        if (builtBy != null) {
            res.builtBy(builtBy)
        }
    }
}

internal fun Project.configureArtifacts(
    spec: AbstractDeploySpec<*>,
    component: Component,
    maven: MavenPublication,
    log: Logger
) {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    val origin = component.origin.get()
    when (origin) {
        is Component.Origin.SoftwareComponent -> {
            log { "configureArtifacts: component-based, calling maven.from(SoftwareComponent)" }
            val softwareComponent = requireNotNull(components[origin.name]) { "Could not find software component ${origin.name}." }
            log { "configureArtifacts: component dump: ${softwareComponent.dump()}" }
            maven.from(softwareComponent)
        }
        is Component.Origin.ArtifactSet -> {
            log { "configureArtifacts: artifact-based, copying artifacts over" }
            maven.addArtifacts(log, origin.artifacts)
        }
        is Component.Origin.MavenPublication -> {
            if (!origin.clone) {
                log { "configureArtifacts: publication-based, no clone, nothing to do" }
            } else {
                log { "configureArtifacts: publication-based, cloning" }
                // Cloning artifacts is tricky - from() will copy some of them,
                // but there might be more and we can't allow duplicates.
                val originalPublication = requireNotNull(publications[origin.name]) { "Could not find maven publication ${origin.name}." }
                originalPublication as MavenPublicationInternal
                maven as MavenPublicationInternal

                // Add the SoftwareComponent, noting that some publications don't have any
                // For example the plugin markers have only a pom file with no other content.
                val originalComponent: SoftwareComponent? = originalPublication.softwareComponentOrNull
                if (originalComponent != null) {
                    maven.from(originalComponent)
                    // This publication is now an alias of the other one. If we don't do this,
                    // inter-project dependencies are broken. https://github.com/gradle/gradle/issues/1061
                    // This is not important when there's no component, such publications are excluded by default.
                    maven.isAlias = true
                    log { "configureArtifacts: clone-based, has SoftwareComponent, making it an alias" }
                    log { "configureArtifacts: component dump: ${originalComponent.dump()}" }

                    // Note that we don't inspect artifacts with getArtifacts() in this branch because it's very dangerous.
                    // getArtifacts() finalizes the underlying component and modifies the DefaultMavenPublication state
                    // irreversibly (e.g. populated flag). This can break many things in the publishing process later on,
                    // if the component was not fully populated.
                } else {
                    log { "configureArtifacts: clone-based, no SoftwareComponent. Using getArtifacts()" }
                    log { "configureArtifacts: trying to clone original publication artifacts..." }
                    fun cloneArtifacts(artifacts: MavenArtifactSet) {
                        log { "configureArtifacts: cloning: ${artifacts.dump()}" }
                        artifacts.configureEach {
                            val contained = maven.artifacts.any { a ->
                                a.classifier == classifier && a.extension == extension
                            }
                            log { "configureArtifacts: Processing artifact ${file.name} (ext:$extension classifier:$classifier contained:$contained)" }
                            if (!contained) maven
                                .artifact(this)
                                .builtBy(this.buildDependencies)
                        }
                    }

                    // NOTE: if a failure happens here, it means that, at the very least, Component.whenConfigurable
                    // implementation is wrong
                    val artifacts = runCatching { originalPublication.artifacts }.getOrNull()
                    if (artifacts != null) {
                        cloneArtifacts(artifacts)
                    } else {
                        log { "configureArtifacts: fetching artifacts NOW failed. Delaying to afterEvaluate." }
                        afterEvaluate { cloneArtifacts(originalPublication.artifacts) }
                    }
                }

                // Copy standalone XML actions. This is especially important for plugin markers
                // in publications setup by java-gradle-plugin, see MavenPluginPublishPlugin.java.
                maven.pom.withXml(originalPublication.pom.xmlAction)
            }
        }
    }

    maven.addArtifacts(log, component.extras)

    // TODO: The two operations down here require getArtifacts() which is a very problematic API (see above)
    //  I removed the existence check but that also means that there might be duplication.
    //  Let's try to fix that downstream maybe.

    // Add sources, but not if they are present already! Otherwise publishing will fail.
    // if (maven.artifacts.none { it.isSourcesJar }) {
        component.sources.orNull?.let {
            log { "configureArtifacts: adding sources to MavenPublication ${maven.name}" }
            maven.artifact(it).builtBy(it)
        }
    // }

    // Add docs, but not if they are present already! Otherwise publishing will fail.
    // if (maven.artifacts.none { it.isDocsJar }) {
        component.docs.orNull?.let {
            log { "configureArtifacts: adding docs to MavenPublication ${maven.name}" }
            maven.artifact(it).builtBy(it)
        }
    // }
}

@Suppress("UNCHECKED_CAST")
private val MavenPublicationInternal.softwareComponentOrNull: SoftwareComponent? get() {
    val tmp: Any? = this::class.java.getMethod("getComponent").invoke(this)
    return when (tmp) {
        null -> null
        is SoftwareComponent -> tmp
        is Provider<*> -> (tmp as Provider<SoftwareComponent>).orNull // Newer Gradle version
        else -> error("Unexpected component type: $tmp")
    }
}