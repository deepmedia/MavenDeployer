package io.deepmedia.tools.deployer.model

import io.deepmedia.tools.deployer.Logger
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.newInstance
import kotlin.reflect.KClass

interface DeploySpec : Named {
    val content: Content
    val auth: Auth
    val projectInfo: ProjectInfo
    val release: Release
    val signing: Signing
}

internal fun DeploySpec.resolve(target: Project) {
    auth.resolve(target, this)
    projectInfo.resolve(target, this)
    release.resolve(target, this)
    signing.resolve(target, this)
    content.resolve(target, this)
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

    internal abstract fun registerRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository
    internal open fun registerInitializationTask(target: Project, name: String, repo: MavenArtifactRepository): TaskProvider<*>? = null
    internal open fun registerFinalizationTask(target: Project, name: String, init: TaskProvider<*>?): TaskProvider<*>? = null

    internal open fun readSignCredentials(target: Project): Pair<String, String>? {
        if (!signing.key.isPresent && !signing.password.isPresent) return null
        val key = signing.key.orNull?.resolve(target, "spec.signing.key") ?: error("Got signing password, but no key.")
        val password = signing.password.orNull?.resolve(target, "spec.signing.password")  ?: error("Got signing key, but no password.")
        return key to password
    }

    internal open fun validateMavenArtifacts(target: Project, artifacts: MavenArtifactSet, log: Logger) = Unit

    internal open fun validateMavenPom(pom: MavenPom) = Unit

    // internal open fun provideDefaultSourcesForComponent(target: Project, component: Component): Any? = null
    // internal open fun provideDefaultDocsForComponent(target: Project, component: Component): Any? = null
}