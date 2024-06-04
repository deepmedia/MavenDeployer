package io.deepmedia.tools.deployer.impl

import io.deepmedia.tools.deployer.Logger
import io.deepmedia.tools.deployer.dump
import io.deepmedia.tools.deployer.model.*
import io.deepmedia.tools.deployer.tasks.isDocsJar
import io.deepmedia.tools.deployer.tasks.isSourcesJar
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.property
import javax.inject.Inject
import kotlin.math.abs

typealias NexusDeploySpec = SonatypeDeploySpec
typealias NexusAuth = SonatypeAuth

class SonatypeDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<SonatypeAuth>(objects, name, SonatypeAuth::class) {

    // OSSRH: Nexus-powered repositories provided by Sonatype to OSS projects for
    // - pushing artifacts to Maven Central (push to ossrh/ossrh1, followed by convoluted close/release/drop API calls)
    // - hosting development builds (push to ssrhSnapshots/ossrhSnapshots1)
    @JvmField val ossrh = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrh1 = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrhSnapshots = "https://oss.sonatype.org/content/repositories/snapshots/"
    @JvmField val ossrhSnapshots1 = "https://s01.oss.sonatype.org/content/repositories/snapshots/"

    val repositoryUrl: Property<String> = objects.property<String>().convention(ossrh1).apply { finalizeValueOnRead() }

    private val maySyncToMavenCentral = repositoryUrl.map { it == ossrh || it == ossrh1 }


    override fun createMavenRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        val repo = repositoryUrl.get()
        return repositories.maven(repo) {
            this.name = abs(repo.hashCode()).toString()
        }
    }

    override fun resolveMavenRepository(target: Project, repository: MavenArtifactRepository) {
        repository.credentials.username = auth.user.get().resolve(target, "spec.auth.user")
        repository.credentials.password = auth.password.get().resolve(target, "spec.auth.password")
    }

    override fun resolveSigning(target: Project): Pair<String, String>? {
        val result = super.resolveSigning(target)
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

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is SonatypeDeploySpec) {
            repositoryUrl.convention(to.repositoryUrl)
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
