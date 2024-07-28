package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.specs.GithubDeploySpec
import io.deepmedia.tools.deployer.specs.LocalDeploySpec
import io.deepmedia.tools.deployer.specs.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import io.deepmedia.tools.deployer.central.ossrh.OssrhService
import io.deepmedia.tools.deployer.central.portal.CentralPortalService
import io.deepmedia.tools.deployer.specs.CentralPortalDeploySpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.component.ComponentWithCoordinates
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsageContext
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets



@Suppress("unused")
class DeployerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        target.plugins.apply("signing")

        val deployer = target.extensions.create("deployer", DeployerExtension::class.java)
        target.whenEvaluated {
            deployer.resolve(target)
        }

        val log = Logger(deployer.verbose, listOf(target.name))

        val deployAll = target.tasks.register("deployAll") {
            this.group = "Deployment"
            this.description = "Deploy all specs."
        }

        // All to force eager creation otherwise we don't create the tasks
        // Note specs are not configured, only the name is reliable
        deployer.specs.all {
            this as AbstractDeploySpec<*>

            if (this is SonatypeDeploySpec) {
                target.gradle.sharedServices.registerIfAbsent(OssrhService.Name, OssrhService::class) {
                    parameters.closeTimeout.set(deployer.mavenCentralSync.closeTimeout.map { it.inWholeMilliseconds })
                    parameters.releaseTimeout.set(deployer.mavenCentralSync.releaseTimeout.map { it.inWholeMilliseconds })
                    parameters.pollingDelay.set(deployer.mavenCentralSync.pollingDelay.map { it.inWholeMilliseconds })
                    parameters.verboseLogging.set(deployer.verbose)
                }
            }
            if (this is CentralPortalDeploySpec) {
                target.gradle.sharedServices.registerIfAbsent(CentralPortalService.Name, CentralPortalService::class) {
                    parameters.timeout.set(deployer.centralPortalSettings.timeout.map { it.inWholeMilliseconds })
                    parameters.pollingDelay.set(deployer.centralPortalSettings.pollingDelay.map { it.inWholeMilliseconds })
                    parameters.verboseLogging.set(deployer.verbose)
                }
            }

            val deployThis = registerDeployTasks(log.child(name), this, target)
            deployAll.configure { dependsOn(deployThis) }
        }

        registerDebugTasks(target)
    }

    private fun registerDeployTasks(log: Logger, spec: AbstractDeploySpec<*>, target: Project): TaskProvider<*> {

        val deployTask = target.tasks.register("deploy${spec.name.capitalized()}") {
            this.group = "Deployment"
            this.description = "Deploy '${spec.name}' spec (${
                when (spec) {
                    is LocalDeploySpec -> "local maven repository"
                    is SonatypeDeploySpec -> "Sonatype/Nexus repository"
                    is GithubDeploySpec -> "GitHub packages"
                    is CentralPortalDeploySpec -> "Central Portal uploads"
                    else -> error("Unexpected spec type: ${spec::class}")
                }
            })."
        }
        log { "Created deploy task '${deployTask.name}'" }

        target.whenEvaluated {
            // now spec was configured by the user. Resolve it and do stuff
            spec.resolve(target)

            // Register repository. This is shared among components
            val publishing = target.extensions.getByType(PublishingExtension::class.java)
            val repository = spec.registerRepository(target, publishing.repositories)
            log { "Created publishing repository '${repository.name}'" }

            // Register preDeploy and postDeploy tasks.
            val preDeployTask = spec.registerInitializationTask(target, "preDeploy${spec.name.capitalized()}", repository)
            val postDeployTask = spec.registerFinalizationTask(target, "postDeploy${spec.name.capitalized()}", preDeployTask)
            deployTask.configure {
                dependsOn(*listOfNotNull(preDeployTask, postDeployTask).toTypedArray())
            }
            if (preDeployTask != null && postDeployTask != null) {
                postDeployTask.configure { mustRunAfter(preDeployTask) }
            }

            // Process components
            log { "Processing ${spec.content.components.size}+ components" }
            val signInfo = spec.readSignCredentials(target)
            spec.content.components.all {
                val publicationTask = registerPublicationTask(log.child(shortName), this, spec, target, repository, signInfo)
                deployTask.configure { dependsOn(publicationTask) }
                publicationTask.configure { if (preDeployTask != null) mustRunAfter(preDeployTask) }
                postDeployTask?.configure { mustRunAfter(publicationTask) }
            }
        }

        return deployTask
    }

    private fun registerPublicationTask(
        log: Logger,
        component: Component,
        spec: AbstractDeploySpec<*>,
        target: Project,
        repository: MavenArtifactRepository,
        signInfo: Pair<String, String>?
    ): TaskProvider<*> {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        val publication = component.maybeCreatePublication(publishing.publications, spec)

        // Repository exists and publication was created. This means that publishing plugin will
        // now have created the publication task, though it still must be configured.
        val mavenPublish = target.tasks.named(
            "publish${publication.name.capitalized()}Publication" +
                    "To${repository.name.capitalized()}Repository"
        )

        // Configure when possible
        log { "Waiting for component to be configurable" }
        component.whenConfigurable(target) {
            log { "Component is now configurable" }
            target.configureArtifacts(spec, component, publication, log)

            // Configure signing if present
            val sign = when (signInfo) {
                null -> null
                else -> target.configureSigning(signInfo, publication, log)
            }
            target.configurePom(spec, component, publication, log)

            // Add maven validation, mostly for sonatype
            mavenPublish.configure {
                if (sign != null) dependsOn(sign)
                onlyIf { component.enabled.get() }
                doFirst {
                    log { "Starting artifact and POM validation"}
                    spec.validateMavenArtifacts(target, publication.artifacts, log)
                    spec.validateMavenPom(publication.pom)
                    log { "Completed artifact and POM validation"}
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