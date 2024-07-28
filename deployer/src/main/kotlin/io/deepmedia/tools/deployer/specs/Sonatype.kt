package io.deepmedia.tools.deployer.specs

import io.deepmedia.tools.deployer.Logger
import io.deepmedia.tools.deployer.dump
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
import io.deepmedia.tools.deployer.central.ossrh.OssrhInfo
import io.deepmedia.tools.deployer.central.ossrh.OssrhService
import io.deepmedia.tools.deployer.central.ossrh.OssrhServer
import io.deepmedia.tools.deployer.tasks.isDocsJar
import io.deepmedia.tools.deployer.tasks.isSourcesJar
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


typealias NexusDeploySpec = SonatypeDeploySpec
typealias NexusAuth = SonatypeAuth
typealias NexusMavenCentralSync = SonatypeMavenCentralSync


open class SonatypeMavenCentralSync @Inject constructor(objects: ObjectFactory) {
    val pollingDelay: Property<Duration> = objects.property<Duration>().convention(15.seconds)
    val closeTimeout: Property<Duration> = objects.property<Duration>().convention(12.minutes)
    val releaseTimeout: Property<Duration> = objects.property<Duration>().convention(6.minutes)
    fun pollingDelay(milliseconds: Long) { pollingDelay.set(milliseconds.milliseconds) }
    fun closeTimeout(milliseconds: Long) { closeTimeout.set(milliseconds.milliseconds) }
    fun releaseTimeout(milliseconds: Long) { releaseTimeout.set(milliseconds.milliseconds) }
}

class SonatypeDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<SonatypeAuth>(objects, name, SonatypeAuth::class) {

    @JvmField val ossrh = OssrhServer.S00.deployUrl
    @JvmField val ossrh1 = OssrhServer.S01.deployUrl
    @JvmField val ossrhSnapshots = OssrhServer.S00.snapshotUrl
    @JvmField val ossrhSnapshots1 = OssrhServer.S01.snapshotUrl

    val repositoryUrl: Property<String> = objects.property<String>().convention(ossrh1).apply { finalizeValueOnRead() }
    private val repositoryName = repositoryUrl.map {
        when (it) {
            ossrh -> "ossrh"
            ossrh1 -> "ossrh1"
            ossrhSnapshots -> "ossrhSnapshots"
            ossrhSnapshots1 -> "ossrhSnapshots1"
            else -> abs(it.hashCode()).toString()
        }
    }

    private val maySyncToMavenCentral = repositoryUrl.map { it == ossrh || it == ossrh1 }

    /**
     * Whether we should create a Nexus staging repository for deployment and promote it to Maven Central later.
     * Note that this is only supposed to work for OSSRH-based Nexus repositories, not for any custom URL.
     * Also, it would not work for OSSRH snapshot repos either.
     */
    val syncToMavenCentral: Property<Boolean> = objects.property<Boolean>().convention(false).apply { finalizeValueOnRead() }

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is SonatypeDeploySpec) {
            repositoryUrl.convention(to.repositoryUrl)
        }
    }

    override fun registerRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        return repositories.maven(repositoryUrl.get()) {
            this.name = repositoryName.get()
        }
    }

    override fun registerInitializationTask(target: Project, name: String, repo: MavenArtifactRepository): TaskProvider<*> {
        return target.tasks.register(name, SonatypeInitializationTask::class).apply {
            configure {
                this.auth.set(this@SonatypeDeploySpec.auth)
                this.repo.set(repo)
                this.sync.set(syncToMavenCentral)
                this.group.set(projectInfo.resolvedGroupId)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun registerFinalizationTask(target: Project, name: String, init: TaskProvider<*>?): TaskProvider<*>? {
        init as TaskProvider<SonatypeInitializationTask>
        return target.tasks.register(name, SonatypeFinalizationTask::class).apply {
            configure { ossrhInfo.set(init.flatMap { it.ossrhInfo }) }
        }
    }

    override fun readSignCredentials(target: Project): Pair<String, String>? {
        val result = super.readSignCredentials(target)
        if (result == null && maySyncToMavenCentral.get()) {
            error("Signing is mandatory for OSSRH/Maven Central deployments. Please add spec.signing.key and spec.signing.password.")
        }
        return result
    }

    /*override fun provideDefaultDocsForComponent(target: Project, component: Component): Any {
        return target.makeDocsJar(this, component, empty = true) // required by sonatype
    }

    override fun provideDefaultSourcesForComponent(target: Project, component: Component): Any? {
        return target.makeSourcesJar(this, component, empty = true) // required by sonatype
    }*/

    override fun validateMavenArtifacts(target: Project, artifacts: MavenArtifactSet, log: Logger) {
        super.validateMavenArtifacts(target, artifacts, log)
        if (maySyncToMavenCentral.get()) {
            fun err(type: String): String {
                return "Sonatype requires a $type jar artifact. Please add it to your component; you may use utilities like emptyDocs() and emptySources(). " +
                        "Available artifacts: " + artifacts.dump()
            }
            require(artifacts.any { it.isSourcesJar }) { err("sources") }
            require(artifacts.any { it.isDocsJar }) { err("javadoc") }
        }

        // It's not possible to use the approach below given when this function is called (inside the task)
        // because it would add a task dependency that will not be respected, because it's too late
        /* var hasSources = false
        var hasDocs = false
        log { "validateMavenArtifacts(${artifacts.size})..."}
        artifacts.forEach {
            log { "validateMavenArtifacts: ${it.dump()}" }
            hasSources = hasSources || it.isSourcesJar
            hasDocs = hasDocs || it.isDocsJar
        }
        if (!hasSources) {
            val make = target.makeEmptySourcesJar
            val artifact = artifacts.artifact(make) { builtBy(make) }
            log { "validateMavenArtifacts: added ${artifact.dump()} (sonatype requirement)"}
        }
        if (!hasDocs) {
            val make = target.makeEmptyDocsJar
            val artifact = artifacts.artifact(make) { builtBy(make) }
            log { "validateMavenArtifacts: added ${artifact.dump()} (sonatype requirement)"}
        } */
    }

    // https://central.sonatype.org/pages/requirements.html
    override fun validateMavenPom(pom: MavenPom) {
        super.validateMavenPom(pom)
        if (maySyncToMavenCentral.get()) {
            require(pom.url.isPresent) {
                "Sonatype POM requires a project url. Please add it to spec.projectInfo.url."
            }
            pom as MavenPomInternal
            require(pom.licenses.isNotEmpty()) {
                "Sonatype POM requires at least one license. Please add it to spec.projectInfo.licenses."
            }
            require(pom.developers.isNotEmpty()) {
                "Sonatype POM requires at least one developer. Please add it to spec.projectInfo.developers."
            }
            require(pom.scm.connection.isPresent && pom.scm.developerConnection.isPresent && pom.scm.url.isPresent) {
                "Sonatype POM requires complete SCM info. Please add it to spec.projectInfo.scm."
            }
        }
    }
}

open class SonatypeAuth @Inject constructor(objects: ObjectFactory) : Auth() {
    val user: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }
    val password: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }

    override fun fallback(to: Auth) {
        if (to is SonatypeAuth) {
            user.convention(to.user)
            password.convention(to.password)
        }
    }
}

internal abstract class SonatypeInitializationTask @Inject constructor(
    objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val layout: ProjectLayout
) : DefaultTask() {
    @get:Input val repo: Property<MavenArtifactRepository> = objects.property()
    @get:Input val auth: Property<SonatypeAuth> = objects.property()
    @get:Input val sync: Property<Boolean> = objects.property()
    @get:Input val group: Property<String> = objects.property()
    @get:Internal val ossrhInfo: Property<OssrhInfo> = objects.property()

    @Suppress("UnstableApiUsage")
    @get:ServiceReference(OssrhService.Name)
    abstract val ossrh: Property<OssrhService>

    @TaskAction
    fun execute() {
        val repo = repo.get()
        val auth = auth.get()
        val username = auth.user.get().resolve(providers, layout, "spec.auth.user")
        val password = auth.password.get().resolve(providers, layout, "spec.auth.password")
        repo.credentials.username = username
        repo.credentials.password = password
        if (sync.get()) {
            val ossrhUrl = OssrhServer.values().firstOrNull { it.deployUrl == repo.url.toString() }
                ?: error("syncToMavenCentral was true, but URL is not ossrh nor ossrh1: ${repo.url}")
            val info = OssrhInfo(ossrhUrl, username, password, group.get())
            val repoUrl = ossrh.get().initialize(info)
            repo.setUrl(repoUrl)
            ossrhInfo.set(info)
        }
    }
}

internal abstract class SonatypeFinalizationTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {
    @get:Optional
    @get:Input
    val ossrhInfo: Property<OssrhInfo> = objects.property()

    @Suppress("UnstableApiUsage")
    @get:ServiceReference(OssrhService.Name)
    abstract val ossrh: Property<OssrhService>

    @TaskAction
    fun execute() {
        val info = ossrhInfo.orNull ?: return
        ossrh.get().finalize(info)
    }
}