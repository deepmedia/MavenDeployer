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
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
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
    when (val origin = component.origin.get()) {
        is Component.Origin.SoftwareComponent -> {
            log { "configureArtifacts: component-based, calling maven.from(SoftwareComponent)" }
            log { "configureArtifacts: component dump: ${origin.component.dump()}" }
            maven.from(origin.component)
        }
        is Component.Origin.SoftwareComponentName -> {
            log { "configureArtifacts: component name-based, calling maven.from(SoftwareComponent)" }
            val softwareComponent = requireNotNull(components[origin.name]) { "Could not find software component ${origin.name}." }
            log { "configureArtifacts: component dump: ${softwareComponent.dump()}" }
            maven.from(softwareComponent)
        }
        is Component.Origin.ArtifactSet -> {
            log { "configureArtifacts: artifact-based, copying artifacts over" }
            maven.addArtifacts(log, origin.artifacts)
        }
        is Component.Origin.MavenPublicationName -> {
            if (!origin.clone) {
                log { "configureArtifacts: publication-based, no clone, nothing to do" }
                // The maven publication we have is already the configured one
            } else {
                log { "configureArtifacts: publication-based, cloning" }
                // Cloning artifacts is tricky - from() will copy some of them,
                // but there might be more and we can't allow duplicates.
                val publications = extensions.getByType(PublishingExtension::class.java).publications
                val source = requireNotNull(publications[origin.name]) { "Could not find maven publication ${origin.name}." }
                clonePublication(source as MavenPublication, maven, log)
            }
        }
    }

    maven.addArtifacts(log, component.extras)

    // TODO: The two operations down here required getArtifacts() which is a very problematic API
    //  because it marks the DefaultMavenPublication as "populated" irreversibly
    //  Removed the check, try to find another way to solve duplications, otherwise publishing can fail

    // if (maven.artifacts.none { it.isSourcesJar }) {
        (component.sources.orNull ?: spec.provideDefaultSourcesForComponent(this, component))?.let {
            log { "configureArtifacts: adding sources to MavenPublication ${maven.name}" }
            if (it is Artifacts.Entry) maven.artifact(it.artifact).builtBy(it.builtBy)
            else maven.artifact(it).builtBy(it)
        }
    // }

    // if (maven.artifacts.none { it.isDocsJar }) {
        (component.docs.orNull ?: spec.provideDefaultDocsForComponent(this, component))?.let {
            log { "configureArtifacts: adding docs to MavenPublication ${maven.name}" }
            if (it is Artifacts.Entry) maven.artifact(it.artifact).builtBy(it.builtBy)
            else maven.artifact(it).builtBy(it)
        }
    // }
}

private fun Project.clonePublication(
    source: MavenPublication,
    destination: MavenPublication,
    log: Logger
) {
    source as MavenPublicationInternal
    destination as MavenPublicationInternal

    // Add the SoftwareComponent, noting that some publications don't have any
    // For example the plugin markers have only a pom file with no other content.
    val originalComponent: SoftwareComponent? = source.softwareComponentOrNull
    if (originalComponent != null) {
        log { "clonePublication: has SoftwareComponent, marking as alias... ${originalComponent.dump()}" }
        destination.from(originalComponent)
        // This publication is now an alias of the other one. If we don't do this,
        // inter-project dependencies are broken. https://github.com/gradle/gradle/issues/1061
        // This is not important when there's no component, such publications are excluded by default.
        destination.isAlias = true

        // Note that we don't inspect artifacts with getArtifacts() in this branch because it's very dangerous.
        // getArtifacts() finalizes the underlying component and modifies the DefaultMavenPublication state
        // irreversibly (e.g. populated flag). This can break many things in the publishing process later on,
        // if the component was not fully populated.
    } else {
        // NOTE: if a failure happens here, it means that, at the very least, Component.whenConfigurable
        // implementation is wrong
        val sourceArtifacts = runCatching { source.artifacts }.getOrNull()
        if (sourceArtifacts != null) {
            log { "clonePublication: no SoftwareComponent, trying to clone original publication artifacts..." }
            cloneArtifacts(sourceArtifacts, destination.artifacts, log)
        } else {
            log { "clonePublication: no SoftwareComponent, but fetching artifacts failed. Delaying to whenEvaluated." }
            project.whenEvaluated { cloneArtifacts(source.artifacts, destination.artifacts, log) }
        }
    }

    // Copy standalone XML actions. This is especially important for plugin markers
    // in publications setup by java-gradle-plugin, see MavenPluginPublishPlugin.java.
    destination.pom.withXml(source.pom.xmlAction)
}

private fun cloneArtifacts(source: MavenArtifactSet, destination: MavenArtifactSet, log: Logger) {
    log { "cloneArtifacts: cloning ${source.dump()}" }
    source.configureEach {
        val contained = destination.any { a ->
            a.classifier == classifier && a.extension == extension
        }
        log { "cloneArtifacts: cloning artifact ${this.dump()} (contained:$contained)..." }
        if (!contained) destination
            .artifact(this)
            .builtBy(this.buildDependencies)
    }
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