package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.DefaultDeploySpec
import io.deepmedia.tools.deployer.impl.GithubDeploySpec
import io.deepmedia.tools.deployer.impl.LocalDeploySpec
import io.deepmedia.tools.deployer.impl.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import io.deepmedia.tools.deployer.tasks.makeAutoDocsJar
import io.deepmedia.tools.deployer.tasks.makeEmptyDocsJar
import io.deepmedia.tools.deployer.tasks.makeEmptySourcesJar
import org.gradle.api.Action
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class DeployerExtension @Inject constructor(target: org.gradle.api.Project) : SecretScope {

    val verbose = target.objects.property<Boolean>().convention(false)

    private val project = target
    private val objects = target.objects

    val defaultSpec = DefaultDeploySpec(target)

    fun defaultSpec(action: Action<DefaultDeploySpec>) {
        action.execute(defaultSpec)
    }

    val specs: PolymorphicDomainObjectContainer<DeploySpec> = objects.polymorphicDomainObjectContainer(DeploySpec::class).apply {
        registerFactory(LocalDeploySpec::class.java) { LocalDeploySpec(objects, it).apply { fallback(defaultSpec) } }
        registerFactory(GithubDeploySpec::class.java) { GithubDeploySpec(objects, it).apply { fallback(defaultSpec) } }
        registerFactory(SonatypeDeploySpec::class.java) { SonatypeDeploySpec(objects, it).apply { fallback(defaultSpec) } }
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

    fun githubSpec(name: String = "github", configure: Action<GithubDeploySpec> = Action {  }) {
        specs.register(specName(name, "github"), GithubDeploySpec::class.java, configure)
    }
}