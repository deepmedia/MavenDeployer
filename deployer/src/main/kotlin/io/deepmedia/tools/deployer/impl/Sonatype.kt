package io.deepmedia.tools.deployer.impl

import io.deepmedia.tools.deployer.fallback
import io.deepmedia.tools.deployer.getSecretOrThrow
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.property
import javax.inject.Inject
import kotlin.math.abs

class SonatypeDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<SonatypeAuth>(objects, name, SonatypeAuth::class) {

    @JvmField val ossrh = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrh1 = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    @JvmField val ossrhSnapshots = "https://oss.sonatype.org/content/repositories/snapshots/"
    @JvmField val ossrhSnapshots1 = "https://s01.oss.sonatype.org/content/repositories/snapshots/"

    val repositoryUrl: Property<String> = objects.property<String>().convention(ossrh1)

    override fun RepositoryHandler.mavenRepository(target: Project): MavenArtifactRepository {
        val repo = repositoryUrl.get()
        return maven(repo) {
            this.name = abs(repo.hashCode()).toString()
            credentials.username = target.getSecretOrThrow(auth.user.get(), "spec.auth.user")
            credentials.password = target.getSecretOrThrow(auth.password.get(), "spec.auth.password")
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
    val user: Property<String> = objects.property()
    val password: Property<String> = objects.property()

    override fun fallback(to: Auth) {
        if (to is SonatypeAuth) {
            user.fallback(to.user)
            password.fallback(to.password)
        }
    }
}
