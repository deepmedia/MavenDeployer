package io.deepmedia.tools.deployer.model

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance
import kotlin.reflect.KClass

interface DeploySpec : Named {
    val content: Content
    val auth: Auth
    val projectInfo: ProjectInfo
    val release: Release
    val signing: Signing
}

class DefaultDeploySpec internal constructor(objects: ObjectFactory) : DeploySpec {
    override fun getName() = "_default"
    override val auth: Auth = objects.newInstance()
    override val content: Content = objects.newInstance()
    override val projectInfo: ProjectInfo = objects.newInstance()
    override val release: Release = objects.newInstance()
    override val signing: Signing = objects.newInstance()
    fun auth(action: Action<Auth>) { action.execute(auth) }
    fun content(action: Action<Content>) { action.execute(content) }
    fun projectInfo(action: Action<ProjectInfo>) { action.execute(projectInfo) }
    fun release(action: Action<Release>) { action.execute(release) }
    fun signing(action: Action<Signing>) { action.execute(signing) }
}

abstract class AbstractDeploySpec<A: Auth> constructor(
    objects: ObjectFactory,
    private val name: String,
    authClass: KClass<A>
): DeploySpec {
    final override val auth: A = objects.newInstance(authClass)
    final override val content: Content = objects.newInstance()
    final override val projectInfo: ProjectInfo = objects.newInstance()
    final override val release: Release = objects.newInstance()
    final override val signing: Signing = objects.newInstance()
    fun auth(action: Action<A>) { action.execute(auth) }
    fun content(action: Action<Content>) { action.execute(content) }
    fun projectInfo(action: Action<ProjectInfo>) { action.execute(projectInfo) }
    fun release(action: Action<Release>) { action.execute(release) }
    fun signing(action: Action<Signing>) { action.execute(signing) }

    override fun getName() = name

    internal open fun fallback(to: DeploySpec) {
        content.fallback(to.content)
        auth.fallback(to.auth)
        projectInfo.fallback(to.projectInfo)
        release.fallback(to.release)
        signing.fallback(to.signing)
    }

    internal fun resolve(target: org.gradle.api.Project) {
        content.resolve(target, this)
        auth.resolve(target, this)
        projectInfo.resolve(target, this)
        release.resolve(target, this)
        signing.resolve(target, this)
    }

    internal abstract fun RepositoryHandler.mavenRepository(target: org.gradle.api.Project): MavenArtifactRepository
}