package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.*
import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Action
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class DeployerExtension @Inject constructor(target: org.gradle.api.Project) : SecretScope, DeploySpec by DefaultDeploySpec(target) {

    val verbose = target.objects.property<Boolean>().convention(false)

    private val objects = target.objects

    @Deprecated("DeployerExtension now *is* the default spec. Simply use `this`.")
    val defaultSpec get() = this

    @Deprecated("DeployerExtension now *is* the default spec. Simply use `this`.")
    fun defaultSpec(action: Action<DeploySpec>) {
        action.execute(defaultSpec)
    }

    init {
        target.whenEvaluated {
            resolve(target)
        }
    }

    fun auth(action: Action<Auth>) { action.execute(auth) }
    fun content(action: Action<Content>) { action.execute(content) }
    fun projectInfo(action: Action<ProjectInfo>) { action.execute(projectInfo) }
    fun release(action: Action<Release>) { action.execute(release) }
    fun signing(action: Action<Signing>) { action.execute(signing) }

    val specs: PolymorphicDomainObjectContainer<DeploySpec> = objects.polymorphicDomainObjectContainer(DeploySpec::class).apply {
        registerFactory(LocalDeploySpec::class.java) { LocalDeploySpec(objects, it).apply { fallback(this@DeployerExtension) } }
        registerFactory(GithubDeploySpec::class.java) { GithubDeploySpec(objects, it).apply { fallback(this@DeployerExtension) } }
        registerFactory(SonatypeDeploySpec::class.java) { SonatypeDeploySpec(objects, it).apply { fallback(this@DeployerExtension) } }
    }

    private fun specName(current: String, default: String): String {
        return if (current.startsWith(default)) current else "$default${current.capitalize()}"
    }

    fun localSpec(name: String = "local", configure: Action<LocalDeploySpec> = Action {  }) {
        specs.register(specName(name, "local"), LocalDeploySpec::class.java, configure)
    }

    fun sonatypeSpec(name: String = "sonatype", configure: Action<SonatypeDeploySpec> = Action {  }) {
        specs.register(specName(name, "sonatype"), SonatypeDeploySpec::class.java, configure)
    }

    fun nexusSpec(name: String = "nexus", configure: Action<NexusDeploySpec> = Action {  }) {
        specs.register(specName(name, "nexus"), NexusDeploySpec::class.java, configure)
    }

    fun githubSpec(name: String = "github", configure: Action<GithubDeploySpec> = Action {  }) {
        specs.register(specName(name, "github"), GithubDeploySpec::class.java, configure)
    }
}