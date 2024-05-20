package io.deepmedia.tools.deployer.impl

import io.deepmedia.tools.deployer.fallback
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject


class GithubDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<GithubAuth>(objects, name, GithubAuth::class) {

    val owner: Property<String> = objects.property()
    val repository: Property<String> = objects.property()

    override fun createMavenRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        val owner = owner.get()
        val repo = repository.orNull ?: target.rootProject.name
        return repositories.maven {
            this.name = "$owner/$repo".hashCode().toString()
            this.url = target.uri("https://maven.pkg.github.com/$owner/$repo")
        }
    }

    override fun resolveMavenRepository(target: Project, repository: MavenArtifactRepository) {
        repository.credentials.username = auth.user.get().resolve(target, "spec.auth.user")
        repository.credentials.password = auth.token.get().resolve(target, "spec.auth.token")
    }

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is GithubDeploySpec) {
            owner.fallback(to.owner)
            repository.fallback(to.repository)
        }
    }
}

open class GithubAuth @Inject constructor(objects: ObjectFactory) : Auth() {
    /** GitHub username. */
    val user: Property<Secret> = objects.property()

    /** GitHub personal access token. */
    val token: Property<Secret> = objects.property()

    override fun fallback(to: Auth) {
        if (to is GithubAuth) {
            user.fallback(to.user)
            token.fallback(to.token)
        }
    }
}