package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.plugins.signing.SigningExtension

private inline fun log(message: () -> String) {
    if (true) {
        println("DeployerPlugin: ${message()}")
    }
}

class DeployerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        log { "apply() started." }
        target.plugins.apply("maven-publish")
        target.plugins.apply("signing")

        val deployer = target.extensions.create("deployer", DeployerExtension::class.java, target)
        val deployAll = target.tasks.register("deployAll")
        target.afterEvaluate {
            deployer.specs.all { // force configuration.
                // TODO: ensure deployment is configured at this point.
                log { "processing spec ${name}..."}
                val spec = this as AbstractDeploySpec<*>
                spec.resolve(target)

                // Create task
                val task = target.tasks.register("deploy${spec.name.capitalize()}")
                deployAll.configure { dependsOn(task) }

                // Configure repository
                val publishing = target.extensions.getByType(PublishingExtension::class.java)
                val repository = with(spec) {
                    publishing.repositories.mavenRepository(target)
                }

                // Process components
                val components = spec.content.resolvedComponents.orNull
                if (components == null || components.isEmpty()) {
                    missingField("content.components")
                }
                components.forEach {
                    val pub = processComponent(target, spec, it, publishing.publications, repository)
                    task.configure { dependsOn(pub) }
                }
            }
        }
    }

    private fun processComponent(
        target: Project,
        spec: AbstractDeploySpec<*>,
        component: Component,
        publications: PublicationContainer,
        repository: MavenArtifactRepository
    ): TaskProvider<*> {
        val origin = component.origin.get()
        val publication = when {
            origin is Component.Origin.SoftwareComponent -> {
                publications.register(origin.publicationName(spec), MavenPublication::class.java) {
                    val softwareComponent = requireNotNull(target.components[origin.name]) {
                        "Could not find software component ${origin.name}."
                    }
                    from(softwareComponent)
                }
            }
            origin is Component.Origin.MavenPublication && origin.clone -> {
                /* var clone = 0
                var cloneName: String
                while (true) {
                    cloneName = if (clone == 0) candidateName else candidateName + clone
                    val exists = runCatching { publications.named(cloneName) }.isSuccess
                    if (exists) clone++ else break
                } */
                publications.register(origin.publicationName(spec), MavenPublication::class.java) {
                    // Cloning artifacts is tricky - from() will copy some of them,
                    // but there might be more and we can't allow duplicates.
                    val mavenPublication = requireNotNull(publications[origin.name]) {
                        "Could not find maven publication ${origin.name}."
                    } as MavenPublicationInternal
                    // Add the SoftwareComponent, noting that some publications don't have any
                    // For example the plugin markers have only a pom file with no other content.
                    val mavenComponent = mavenPublication.component
                    if (mavenComponent != null) { from(mavenComponent) }
                    // Copy standalone XML actions. This is especially important for plugin markers
                    // in publications setup by java-gradle-plugin, see MavenPluginPublishPlugin.java.
                    pom.withXml(mavenPublication.pom.xmlAction)
                    // Care about artifacts and dependencies.
                    mavenPublication.artifacts.forEach {
                        val contained = artifacts.any { a ->
                            a.classifier == it.classifier && a.extension == it.extension
                        }
                        if (!contained) artifact(it)
                    }
                }
            }
            else -> {
                origin as Component.Origin.MavenPublication
                publications.named(origin.name)
            }
        }

        val publishTask = target.tasks.named("publish${publication.name.capitalize()}PublicationTo${repository.name.capitalize()}Repository")
        publication.configure {
            log { "process(${spec.name}): configuring publication..."}
            val maven = this as MavenPublication
            configureMavenPublication(target, spec, component, maven)
        }
        return publishTask
    }

    private fun configureMavenPublication(
        target: Project,
        spec: DeploySpec,
        component: Component,
        maven: MavenPublication
    ) {
        maven.groupId = spec.projectInfo.resolvedGroupId.get().let { base ->
            component.groupId.orNull?.transform(base) ?: base
        }
        maven.artifactId = spec.projectInfo.resolvedArtifactId.get().let { base ->
            component.artifactId.orNull?.transform(base) ?: base
        }
        maven.version = spec.release.resolvedVersion.get()
        maven.pom.name.set(spec.projectInfo.resolvedName)
        maven.pom.url.set(spec.projectInfo.url)
        maven.pom.description.set(spec.projectInfo.resolvedDescription)
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

        // Add sources, but not if they are present already! Otherwise publishing will fail.
        if (maven.artifacts.none { it.classifier == "sources" && it.extension == "jar" }) {
            component.sources.orNull?.let {
                maven.artifact(it).builtBy(it)
            }
        }

        // Add docs, but not if they are present already! Otherwise publishing will fail.
        if (maven.artifacts.none { it.classifier == "javadoc" && it.extension == "jar" }) {
            component.docs.orNull?.let {
                maven.artifact(it).builtBy(it)
            }
        }

        // Configure signing if present
        val signing = spec.signing
        if (signing.key.isPresent || signing.password.isPresent) {
            val ext = target.extensions.getByType(SigningExtension::class)
            val key = target.getSecretOrThrow(signing.key.get(), "spec.signing.key")
            val password = target.getSecretOrThrow(signing.password.get(), "spec.signing.password")
            ext.useInMemoryPgpKeys(key, password)
            try {
                ext.sign(maven)
            } catch (e: Throwable) {
                target.logger.log(
                    LogLevel.WARN, "Two or more specs share the same MavenPublication under the hood! " +
                            "Only one of the signatures will be used, and other configuration parameters " +
                            "might be conflicting as well.")
            }
        }
    }
}