package io.deepmedia.tools.deployer.impl

import io.deepmedia.tools.deployer.fallback
import io.deepmedia.tools.deployer.isJavadocJar
import io.deepmedia.tools.deployer.isSourcesJar
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
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
import org.jetbrains.kotlin.gradle.utils.`is`
import javax.inject.Inject
import kotlin.math.abs

class SonatypeDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<SonatypeAuth>(objects, name, SonatypeAuth::class) {

    @JvmField val ossrh = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrh1 = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrhSnapshots = "https://oss.sonatype.org/content/repositories/snapshots/"
    @JvmField val ossrhSnapshots1 = "https://s01.oss.sonatype.org/content/repositories/snapshots/"

    val repositoryUrl: Property<String> = objects.property<String>().convention(ossrh1)

    override fun createMavenRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        val repo = repositoryUrl.get()
        return repositories.maven(repo) {
            this.name = abs(repo.hashCode()).toString()
            credentials.username = auth.user.get().resolve(target, "spec.auth.user")
            credentials.password = auth.password.get().resolve(target, "spec.auth.password")
        }
    }

    override fun hasSigning(target: Project): Boolean {
        require(super.hasSigning(target)) {
            "Signing is mandatory for Sonatype deployments. Please add spec.signing.key and spec.signing.password."
        }
        return true
    }

    override fun validateMavenArtifacts(artifacts: MavenArtifactSet) {
        super.validateMavenArtifacts(artifacts)
        require(artifacts.any { it.isSourcesJar }) {
            "Sonatype requires a sources jar artifact. Please add it to your component."
        }
        require(artifacts.any { it.isJavadocJar }) {
            "Sonatype requires a javadoc jar artifact. Please add it to your component."
        }
    }

    // https://central.sonatype.org/pages/requirements.html
    override fun validateMavenPom(pom: MavenPom) {
        super.validateMavenPom(pom)
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

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is SonatypeDeploySpec) {
            repositoryUrl.fallback(to.repositoryUrl)
        }
    }
}

open class SonatypeAuth @Inject constructor(objects: ObjectFactory) : Auth() {
    val user: Property<Secret> = objects.property()
    val password: Property<Secret> = objects.property()

    override fun fallback(to: Auth) {
        if (to is SonatypeAuth) {
            user.fallback(to.user)
            password.fallback(to.password)
        }
    }
}
