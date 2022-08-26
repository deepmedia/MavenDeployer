package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Component
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension

internal fun Project.configureArtifacts(
    spec: AbstractDeploySpec<*>,
    component: Component,
    maven: MavenPublication,
) {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    val origin = component.origin.get()
    when {
        origin is Component.Origin.SoftwareComponent -> {
            log { "configureArtifacts(${spec.name}/${origin.name}): component-based" }
            val softwareComponent = requireNotNull(components[origin.name]) { "Could not find software component ${origin.name}." }
            maven.from(softwareComponent)
        }
        origin is Component.Origin.MavenPublication && !origin.clone -> {
            log { "configureArtifacts(${spec.name}/${origin.name}): publication-based, nothing to do" }
        }
        else -> {
            origin as Component.Origin.MavenPublication
            log { "configureArtifacts(${spec.name}/${origin.name}): publication-based, cloning" }
            // Cloning artifacts is tricky - from() will copy some of them,
            // but there might be more and we can't allow duplicates.
            val originalPublication = requireNotNull(publications[origin.name]) { "Could not find maven publication ${origin.name}." }
            originalPublication as MavenPublicationInternal
            maven as MavenPublicationInternal

            // Add the SoftwareComponent, noting that some publications don't have any
            // For example the plugin markers have only a pom file with no other content.
            val originalComponent = originalPublication.component
            if (originalComponent != null) {
                maven.from(originalComponent)
                // This publication is now an alias of the other one. If we don't do this,
                // inter-project dependencies are broken. https://github.com/gradle/gradle/issues/1061
                // This is not important when there's no component, such publications are excluded by default.
                maven.isAlias = true
                log { "configureArtifacts(${spec.name}/${origin.name}): clone-based, has SoftwareComponent, making it an alias" }
            } else {
                log { "configureArtifacts(${spec.name}/${origin.name}): clone-based, no SoftwareComponent" }
            }
            // Copy standalone XML actions. This is especially important for plugin markers
            // in publications setup by java-gradle-plugin, see MavenPluginPublishPlugin.java.
            maven.pom.withXml(originalPublication.pom.xmlAction)
            // Care about artifacts and dependencies.
            log { "configureArtifacts(${spec.name}/${origin.name}): trying to clone original publication artifacts: ${originalPublication.artifacts.dump()}" }
            originalPublication.artifacts.configureEach {
                val contained = maven.artifacts.any { a ->
                    a.classifier == classifier && a.extension == extension
                }
                log { "configureArtifacts(${spec.name}/${origin.name}): processing artifact ${file.name} - $extension - $classifier, contained=$contained" }
                if (!contained) maven
                    .artifact(this)
                    .builtBy(this.buildDependencies)
            }
        }
    }

    // Add sources, but not if they are present already! Otherwise publishing will fail.
    if (maven.artifacts.none { it.isSourcesJar }) {
        component.sources.orNull?.let {
            log { "${spec.name}: adding sources to MavenPublication ${maven.name}" }
            maven.artifact(it).builtBy(it)
        }
    }

    // Add docs, but not if they are present already! Otherwise publishing will fail.
    if (maven.artifacts.none { it.isJavadocJar }) {
        component.docs.orNull?.let {
            log { "${spec.name}: adding docs to MavenPublication ${maven.name}" }
            maven.artifact(it).builtBy(it)
        }
    }

    log { "configureArtifacts(${spec.name}): final artifacts: ${maven.artifacts.dump()}" }
}

internal fun Project.configureSigning(
    spec: AbstractDeploySpec<*>,
    maven: MavenPublication,
) {
    // Configure signing if present
    if (spec.hasSigning(this)) {
        log { "configureSigning(${spec.name}): signing MavenPublication ${maven.name}" }
        val ext = extensions.getByType(SigningExtension::class)
        val key = spec.signing.key.get().resolve(this, "spec.signing.key")
        val password = spec.signing.password.get().resolve(this, "spec.signing.password")
        ext.useInMemoryPgpKeys(key, password)
        try {
            ext.sign(maven)
        } catch (e: Throwable) {
            logger.log(
                LogLevel.WARN, "Two or more specs share the same MavenPublication under the hood! " +
                        "Only one of the signatures will be used, and other configuration parameters " +
                        "might be conflicting as well.")
        }
    }
}

internal fun Project.configurePom(
    spec: AbstractDeploySpec<*>,
    component: Component,
    maven: MavenPublication
) {
    log { "configurePom(${spec.name}): configuring MavenPublication ${maven.name}" }
    maven.groupId = spec.projectInfo.resolvedGroupId.get().let { base ->
        component.groupId.orNull?.transform(base) ?: base
    }
    maven.artifactId = spec.projectInfo.resolvedArtifactId.get().let { base ->
        component.artifactId.orNull?.transform(base) ?: base
    }
    maven.version = spec.release.resolvedVersion.get()
    maven.pom.name.set(spec.projectInfo.resolvedName)
    maven.pom.url.set(spec.projectInfo.url)
    val description = spec.projectInfo.resolvedDescription.zip(spec.release.resolvedDescription) { pr, r ->
        when {
            r.isBlank() -> pr
            pr.isBlank() -> r
            else -> "$pr ($r)"
        }
    }
    maven.pom.description.set(description)
    spec.release.resolvedPackaging.orNull?.let { maven.pom.packaging = it }
    maven.pom.licenses {
        spec.projectInfo.licenses.forEach {
            license {
                name.set(it.id)
                url.set(it.url)
            }
        }
    }
    maven.pom.developers {
        spec.projectInfo.developers.forEach {
            developer {
                name.set(it.name)
                email.set(it.email)
                organization.set(it.organization)
                organizationUrl.set(it.url)
            }
        }
    }
    maven.pom.scm {
        tag.set(spec.release.resolvedTag)
        url.set(spec.projectInfo.scm.sourceUrl.map { it.transform(spec.release.resolvedTag.get()) })
        connection.set(spec.projectInfo.scm.connection)
        developerConnection.set(spec.projectInfo.scm.developerConnection)
    }
}