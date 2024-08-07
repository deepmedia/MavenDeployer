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

internal fun Project.configurePom(
    spec: AbstractDeploySpec<*>,
    component: Component,
    maven: MavenPublication,
    log: Logger
) {
    log { "configurePom: configuring MavenPublication ${maven.name}" }
    maven.groupId = spec.projectInfo.resolvedGroupId.get().let { base ->
        val transformed = component.groupId.orNull?.transform(base)
        log { "configurePom: computing groupId. base=$base unresolved=${spec.projectInfo.groupId.orNull} transformed=$transformed" }
        transformed ?: base
    }
    maven.artifactId = spec.projectInfo.resolvedArtifactId(component).get().also {
        log { "configurePom: computing artifactId: PI=${spec.projectInfo.artifactId.orNull} PI(resolved)=${spec.projectInfo.resolvedArtifactId.orNull} => $it" }
    }
    maven.version = spec.release.resolvedVersion.get()
    maven.pom.name.set(spec.projectInfo.resolvedName)
    maven.pom.url.set(spec.projectInfo.url)
    val description = spec.projectInfo.resolvedDescription.orElse("")
        .zip(spec.release.resolvedDescription.orElse("")) { pr, r ->
        when {
            r.isBlank() -> pr
            pr.isBlank() -> r
            else -> "$pr ($r)"
        }
    }
    maven.pom.description.set(description)
    component.packaging.orNull?.let { maven.pom.packaging = it }
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
    maven.pom.withXml {
        val publications = this@configurePom.extensions.getByType(PublishingExtension::class.java).publications
        component.xml?.invoke(this, spec, publications)
    }
}