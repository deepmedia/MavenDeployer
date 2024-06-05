package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.model.*
import io.deepmedia.tools.deployer.specs.*
import org.gradle.api.Action
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class DeployerExtension @Inject constructor(private val objects: ObjectFactory) : SecretScope, DeploySpec by DefaultDeploySpec(objects) {

    val verbose = objects.property<Boolean>().convention(false)

    // TODO: make this public. The problem is that OssrhService is a shared service and so it doesn't make sense to
    //  configure values in a single gradle subproject (the first one would win)
    internal val mavenCentralSync = objects.newInstance<SonatypeMavenCentralSync>()
    internal fun mavenCentralSync(action: Action<SonatypeMavenCentralSync>) { action.execute(mavenCentralSync) }

    @Deprecated("DeployerExtension now *is* the default spec. Simply use `this`.")
    val defaultSpec get() = this

    @Deprecated("DeployerExtension now *is* the default spec. Simply use `this`.")
    fun defaultSpec(action: Action<DeploySpec>) {
        action.execute(defaultSpec)
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
        return if (current.startsWith(default)) current else "$default${current.capitalized()}"
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