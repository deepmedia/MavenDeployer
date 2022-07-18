package io.deepmedia.tools.deployer

import io.deepmedia.tools.deployer.impl.GithubDeploySpec
import io.deepmedia.tools.deployer.impl.LocalDeploySpec
import io.deepmedia.tools.deployer.impl.SonatypeDeploySpec
import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Action
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.kotlin.dsl.polymorphicDomainObjectContainer
import javax.inject.Inject

open class DeployerExtension @Inject constructor(target: org.gradle.api.Project) {

    private val objects = target.objects

    val defaultSpec = DefaultDeploySpec(objects)

    fun defaultSpec(action: Action<DefaultDeploySpec>) {
        action.execute(defaultSpec)
    }

    val specs: PolymorphicDomainObjectContainer<DeploySpec> = objects.polymorphicDomainObjectContainer(DeploySpec::class).apply {
        registerFactory(LocalDeploySpec::class.java) { LocalDeploySpec(objects, it).apply { fallback(defaultSpec) } }
        registerFactory(GithubDeploySpec::class.java) { GithubDeploySpec(objects, it).apply { fallback(defaultSpec) } }
        registerFactory(SonatypeDeploySpec::class.java) { SonatypeDeploySpec(objects, it).apply { fallback(defaultSpec) } }
    }

    private fun fullname(current: String, default: String): String {
        return if (current.startsWith(default)) current else "$default${current.capitalize()}"
    }

    fun localSpec(name: String = "local", configure: Action<LocalDeploySpec> = Action {  }) {
        val fullname = fullname(name, "local")
        specs.register(fullname, LocalDeploySpec::class.java, configure)
    }

    fun sonatypeSpec(name: String = "sonatype", configure: Action<SonatypeDeploySpec> = Action {  }) {
        val fullname = fullname(name, "sonatype")
        specs.register(fullname, SonatypeDeploySpec::class.java, configure)
    }

    fun githubSpec(name: String = "github", configure: Action<GithubDeploySpec> = Action {  }) {
        val fullname = fullname(name, "github")
        specs.register(fullname, GithubDeploySpec::class.java, configure)
    }
}