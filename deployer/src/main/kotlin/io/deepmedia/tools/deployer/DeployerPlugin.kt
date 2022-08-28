package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.GithubDeploySpec
import io.deepmedia.tools.deployer.impl.LocalDeploySpec
import io.deepmedia.tools.deployer.impl.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*

internal inline fun Project.log(message: () -> String) {
    if (extensions.getByType<DeployerExtension>().verbose.get()) {
        println("[DeployerPlugin] ${message()}")
    }
}

@Suppress("unused")
class DeployerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        target.plugins.apply("signing")

        val deployer = target.extensions.create("deployer", DeployerExtension::class.java, target)
        val deployAll = target.tasks.register("deployAll") {
            this.group = "Deployment"
            this.description = "Deploy all specs."
        }
        target.afterEvaluate {
            // force eager creation otherwise we don't create the tasks
            deployer.specs.all {
                // NOTE: we assume that the spec is configured at this point
                // (which it should be thanks to afterEvaluate {})
                log { "${name}: processing"}
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
                log { "${spec.name}: created deploy task '${deploy.name}'" }
                deployAll.configure { dependsOn(deploy) }

                // Configure repository
                val publishing = target.extensions.getByType(PublishingExtension::class.java)
                val repository = spec.createMavenRepository(target, publishing.repositories)
                log { "${spec.name}: created publishing repository '${repository.name}'" }

                // Process components
                log { "${spec.name}: processing ${spec.content.components.size}+ components" }
                spec.content.components.configureEach {
                    val component = this
                    val publication = maybeCreatePublication(publishing.publications, spec)
                    component.whenConfigurable(target) {
                        log { "${spec.name}: component is now configurable" }
                        target.configureArtifacts(spec, component, publication)
                        target.configureSigning(spec, publication)
                        target.configurePom(spec, component, publication)

                        // Add maven validation, mostly for sonatype
                        val mavenPublish = tasks.named("publish${publication.name.capitalize()}Publication" +
                                "To${repository.name.capitalize()}Repository")
                        mavenPublish.configure {
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