package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.GithubDeploySpec
import io.deepmedia.tools.deployer.impl.LocalDeploySpec
import io.deepmedia.tools.deployer.impl.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ComponentWithCoordinates
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsageContext
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets


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

        // All to force eager creation otherwise we don't create the tasks
        // Note specs are not configured, only the name is reliable
        deployer.specs.all {
            val deployThis = registerDeployTask(
                log = log.child(name),
                spec = this as AbstractDeploySpec<*>,
                target = target,
            )
            deployAll.configure { dependsOn(deployThis) }
        }

        registerDebugTasks(target)
    }

    private fun registerDeployTask(log: Logger, spec: AbstractDeploySpec<*>, target: Project): TaskProvider<*> {
        val deployTask = target.tasks.register("deploy${spec.name.capitalize()}") {
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
        log { "Created deploy task '${deployTask.name}'" }

        target.whenEvaluated {
            // now spec was configured by the user. Resolve it and do stuff
            spec.resolve(target)

            // Configure repository. This is shared among components
            val publishing = target.extensions.getByType(PublishingExtension::class.java)
            val repository = spec.createMavenRepository(target, publishing.repositories)
            log { "Created publishing repository '${repository.name}'" }

            // Process components
            log { "Processing ${spec.content.components.size}+ components" }
            spec.content.components.configureEach {
                val logger = log.child(shortName)
                val publicationTask = registerPublicationTask(logger, this, spec, target, repository)
                deployTask.configure { dependsOn(publicationTask) }
            }
        }

        return deployTask
    }

    private fun registerPublicationTask(
        log: Logger,
        component: Component,
        spec: AbstractDeploySpec<*>,
        target: Project,
        repository: MavenArtifactRepository
    ): TaskProvider<*> {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        val publication = component.maybeCreatePublication(publishing.publications, spec)

        // Repository exists and publication was created. This means that publishing plugin will
        // now have created the publication task, though it still must be configured.
        val mavenPublish = target.tasks.named(
            "publish${publication.name.capitalize()}Publication" +
                    "To${repository.name.capitalize()}Repository"
        )

        // Configure when possible
        log { "Waiting for component to be configurable" }
        component.whenConfigurable(target) {
            log { "Component is now configurable" }
            target.configureArtifacts(spec, component, publication, log)
            val sign = target.configureSigning(spec, publication, log)
            target.configurePom(spec, component, publication, log)

            // Add maven validation, mostly for sonatype
            mavenPublish.configure {
                if (sign != null) dependsOn(sign)
                onlyIf { component.enabled.get() }
                doFirst {
                    spec.configureMavenRepository(target, repository)
                    spec.validateMavenArtifacts(target, publication.artifacts)
                    spec.validateMavenPom(publication.pom)
                }
            }
        }

        return mavenPublish
    }


    private fun registerDebugTasks(target: Project) {
        target.tasks.register("printProjectComponents") {
            fun inspectComponent(inset: String, depth: Int, component: SoftwareComponent) {
                var prefix = inset.repeat(depth)
                println("$prefix ${component.name} (${component})")
                prefix = inset.repeat(depth+1)

                if (component is ComponentWithCoordinates) {
                    println("$prefix has coordinates: ${component.coordinates.group}:${component.coordinates.name}:${component.coordinates.version}")
                }

                if (component is SoftwareComponentInternal && component.usages.isNotEmpty()) {
                    println("$prefix has ${component.usages.size} variants")
                    component.usages.forEach {
                        if (it is KotlinUsageContext) {
                            println("$prefix has variant ${it.name}: compilation=${it.compilation.name} mavenScope=${it.mavenScope}")
                        } else {
                            println("$prefix has variant ${it.name}")
                        }
                    }
                }

                if (component is ComponentWithVariants && component.variants.isNotEmpty()) {
                    println("$prefix has ${component.variants.size} children: ${component.variants.map { it.name }}")
                    component.variants.forEach {
                        inspectComponent(inset, depth+2, it)
                    }
                }
            }
            doLast {
                println("Inspecting Project.components...")
                target.components.forEach {
                    inspectComponent("⚡️", 1, it)
                }
                if (target.isKotlinProject) {
                    target.kotlinExtension.targets.forEach {
                        println("Inspecting components of kotlin target ${it.targetName}...")
                        it.components.forEach {
                            inspectComponent("🍩", 1, it)
                        }
                    }
                }
            }
        }
    }
}