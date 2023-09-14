package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.GithubDeploySpec
import io.deepmedia.tools.deployer.impl.LocalDeploySpec
import io.deepmedia.tools.deployer.impl.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.*


internal class Logger(private val project: Project, private val tags: List<String>) {
    val prefix = tags.joinToString(
        separator = " ",
        transform = { "[$it]" }
    )

    val verbose get() = project.extensions.getByType<DeployerExtension>().verbose.get()

    inline operator fun invoke(message: () -> String) {
        if (verbose) println("$prefix ${message()}")
    }

    fun child(tag: String) = Logger(project, tags + tag)
}

@Suppress("unused")
class DeployerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        target.plugins.apply("signing")

        val log = Logger(target, listOf("DeployerPlugin", target.name))

        val deployer = target.extensions.create("deployer", DeployerExtension::class.java, target)
        val deployAll = target.tasks.register("deployAll") {
            this.group = "Deployment"
            this.description = "Deploy all specs."
        }
        target.afterEvaluate {
            // force eager creation otherwise we don't create the tasks
            deployer.specs.all {
                val log = log.child(name)
                // NOTE: we assume that the spec is configured at this point
                // (which it should be thanks to afterEvaluate {})
                log { "START" }
                val spec = this as AbstractDeploySpec<*>
                spec.resolve(target)

                // Create deploy task
                val deploy = target.tasks.register("deploy${spec.name.capitalize()}") {
                    this.group = "Deployment"
                    this.description = "Deploy '${spec.name}' spec (${
                        when (spec) {
                            is LocalDeploySpec -> "local maven repository"
                            is SonatypeDeploySpec -> "Sonatype repository"
                            is GithubDeploySpec -> "GitHub packages"
                            else -> error("Unexpected spec type: ${spec::class}")
                        }
                    })."
                }
                log { "Created deploy task '${deploy.name}'" }
                deployAll.configure { dependsOn(deploy) }

                // Configure repository
                val publishing = target.extensions.getByType(PublishingExtension::class.java)
                val repository = spec.createMavenRepository(target, publishing.repositories)
                log { "Created publishing repository '${repository.name}'" }

                // Process components
                log { "Processing ${spec.content.components.size}+ components" }
                spec.content.components.configureEach {
                    val log = log.child(when (val o = origin.get()) {
                        is Component.Origin.SoftwareComponent -> "soft:${o.name}"
                        is Component.Origin.MavenPublication -> "pub:${o.name}"
                        is Component.Origin.ArtifactSet -> "art:${o.id}"
                    })
                    val component = this
                    val publication = maybeCreatePublication(publishing.publications, spec)
                    component.whenConfigurable(target) {
                        log { "Component is now configurable" }
                        target.configureArtifacts(spec, component, publication, log)
                        val sign = target.configureSigning(spec, publication, log)
                        target.configurePom(spec, component, publication, log)

                        // Add maven validation, mostly for sonatype
                        val mavenPublish = tasks.named("publish${publication.name.capitalize()}Publication" +
                                "To${repository.name.capitalize()}Repository")
                        mavenPublish.configure {
                            if (sign != null) dependsOn(sign)
                            onlyIf { component.enabled.get() }
                            doFirst {
                                spec.configureMavenRepository(target, repository)
                                spec.validateMavenArtifacts(target, publication.artifacts)
                                spec.validateMavenPom(publication.pom)
                            }
                        }

                        // Depend on the maven-publish task
                        deploy.configure { dependsOn(mavenPublish) }
                    }
                }
            }
        }
    }
}