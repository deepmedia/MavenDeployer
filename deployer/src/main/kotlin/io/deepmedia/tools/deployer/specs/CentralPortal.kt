package io.deepmedia.tools.deployer.specs

import io.deepmedia.tools.deployer.Logger
import io.deepmedia.tools.deployer.dump
import io.deepmedia.tools.deployer.central.portal.CentralPortalInfo
import io.deepmedia.tools.deployer.central.portal.CentralPortalService
import io.deepmedia.tools.deployer.model.*
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
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


open class CentralPortalSettings @Inject constructor(objects: ObjectFactory) {
    val pollingDelay: Property<Duration> = objects.property<Duration>().convention(15.seconds)
    fun pollingDelay(milliseconds: Long) { pollingDelay.set(milliseconds.milliseconds) }

    val timeout: Property<Duration> = objects.property<Duration>().convention(15.minutes)
    fun timeout(milliseconds: Long) { timeout.set(milliseconds.milliseconds) }
}

class CentralPortalDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<CentralPortalAuth>(objects, name, CentralPortalAuth::class) {

    val allowMavenCentralSync: Property<Boolean> = objects.property<Boolean>()
        .convention(true)
        .apply { finalizeValueOnRead() }

    override fun registerRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        val storageRepoName = "${this@CentralPortalDeploySpec.name}Storage"
        val storageRepoDir = target.layout.buildDirectory.get().dir("deployer").dir(storageRepoName)
        return repositories.maven(storageRepoDir) {
            name = storageRepoName
        }
    }

    override fun registerInitializationTask(target: Project, name: String, repo: MavenArtifactRepository): TaskProvider<*> {
        return target.tasks.register(name, CentralPortalInitializationTask::class).apply {
            configure {
                this.auth.set(this@CentralPortalDeploySpec.auth)
                this.storageRepository.set(repo.url.path)
                this.allowSync.set(allowMavenCentralSync)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun registerFinalizationTask(target: Project, name: String, init: TaskProvider<*>?): TaskProvider<*>? {
        init as TaskProvider<CentralPortalInitializationTask>
        return target.tasks.register(name, CentralPortalFinalizationTask::class).apply {
            configure {
                info.set(init.flatMap { it.info })
                rootBuildDir.set(target.rootProject.layout.buildDirectory.get().asFile.absolutePath)
            }
        }
    }

    override fun readSignCredentials(target: Project): Signing.Credentials = when (val result = super.readSignCredentials(target)) {
        Signing.NotDeclared -> error("Signing is mandatory for Central Portal deployments. Please add spec.signing.key and spec.signing.password.")
        else -> result
    }

    override fun validateMavenArtifacts(target: Project, artifacts: MavenArtifactSet, log: Logger) {
        super.validateMavenArtifacts(target, artifacts, log)
        fun err(type: String): String {
            return "Central Portal requires a $type jar artifact. Please add it to your component; you may use utilities like emptyDocs() and emptySources(). " +
                    "Available artifacts: " + artifacts.dump()
        }
        require(artifacts.any { it.isSourcesJar }) { err("sources") }
        require(artifacts.any { it.isDocsJar }) { err("javadoc") }
    }

    // https://central.sonatype.org/pages/requirements.html
    override fun validateMavenPom(pom: MavenPom) {
        super.validateMavenPom(pom)
        require(pom.url.isPresent) {
            "Central Portal POM requires a project url. Please add it to spec.projectInfo.url."
        }
        pom as MavenPomInternal
        require(pom.licenses.isNotEmpty()) {
            "Central Portal POM requires at least one license. Please add it to spec.projectInfo.licenses."
        }
        require(pom.developers.isNotEmpty()) {
            "Central Portal POM requires at least one developer. Please add it to spec.projectInfo.developers."
        }
        val scm = requireNotNull(pom.scm) { "Central Portal POM requires complete SCM info. Please add it to spec.projectInfo.scm." }
        require(scm.connection.isPresent && scm.developerConnection.isPresent && scm.url.isPresent) {
            "Central Portal POM requires complete SCM info. Please add it to spec.projectInfo.scm."
        }
    }
}

open class CentralPortalAuth @Inject constructor(objects: ObjectFactory) : Auth() {
    val user: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }
    val password: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }

    override fun fallback(to: Auth) {
        if (to is SonatypeAuth) {
            user.convention(to.user)
            password.convention(to.password)
        }
    }
}

/**
 * Note: We want [CentralPortalInitializationTask] and [CentralPortalFinalizationTask] to always run
 * regardless of task cache and up to date checks. There are two options:
 *
 * 1. Add `outputs.upToDateWhen { false }` explicitly to tell Gradle this task is never up-to-date.
 *   Turns out that if a task normally declares no outputs, adding the lambda above has important consequences.
 *   It tells Gradle that this task has output logic and enables input fingerprinting, which in turn
 *   activates the requirement that all input properties must be serializable.
 *   So, with that line we start to get errors on [CentralPortalAuth], [CentralPortalInfo]...
 *   To fix they may be split in their individual component (e.g. secret key) but second option is easier.
 *
 * 2. Remember that when a task has no declared outputs, Gradle already considers it never up-to-date.
 *   So doing nothing seems to be the best solution, as not having outputs also means Gradle won't bother
 *   doing input fingerprinting and we don't have to split the current inputs into many primitive properties.
 */
internal abstract class CentralPortalInitializationTask @Inject constructor(
    objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val layout: ProjectLayout
) : DefaultTask() {
    @get:Input val auth: Property<CentralPortalAuth> = objects.property()
    @get:Input val allowSync: Property<Boolean> = objects.property()
    @get:Input val storageRepository: Property<String> = objects.property()
    @get:Internal val info: Property<CentralPortalInfo> = objects.property()

    @Suppress("UnstableApiUsage")
    @get:ServiceReference(CentralPortalService.Name)
    abstract val service: Property<CentralPortalService>

    @TaskAction
    fun execute() {
        val auth = auth.get()
        val username = auth.user.get().resolveOrThrow(providers, layout, "spec.auth.user")
        val password = auth.password.get().resolveOrThrow(providers, layout, "spec.auth.password")
        val info = CentralPortalInfo(username, password, allowSync.get())
        val storageRepository = File(storageRepository.get())
        storageRepository.delete() // Delete any stale data
        this.service.get().initialize(info, storageRepository)
        this.info.set(info)
    }
}

/**
 * See comments on [CentralPortalInitializationTask].
 */
internal abstract class CentralPortalFinalizationTask @Inject constructor(
    objects: ObjectFactory,
) : DefaultTask() {
    @get:Input
    val info: Property<CentralPortalInfo> = objects.property()

    @get:Input
    val rootBuildDir: Property<String> = objects.property()

    @Suppress("UnstableApiUsage")
    @get:ServiceReference(CentralPortalService.Name)
    abstract val service: Property<CentralPortalService>

    @TaskAction
    fun execute() {
        service.get().finalize(info.get(), File(rootBuildDir.get()))
    }
}