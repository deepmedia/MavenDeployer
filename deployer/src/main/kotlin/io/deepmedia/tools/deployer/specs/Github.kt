package io.deepmedia.tools.deployer.specs

import io.deepmedia.tools.deployer.capitalized
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.model.Secret
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.AbstractTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import javax.inject.Inject


class GithubDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<GithubAuth>(objects, name, GithubAuth::class) {

    val owner: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }
    val repository: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }

    override fun registerRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        val owner = owner.get()
        val repo = repository.orNull ?: target.rootProject.name
        return repositories.maven {
            this.name = "$owner${repo.capitalized()}"
            this.url = target.uri("https://maven.pkg.github.com/$owner/$repo")
        }
    }

    override fun registerInitializationTask(
        target: Project,
        name: String,
        repo: MavenArtifactRepository
    ): TaskProvider<*> {
        return target.tasks.register(name, GithubInitializationTask::class).apply {
            configure {
                auth.set(this@GithubDeploySpec.auth)
                credentials.set(repo.credentials)
            }
        }
    }

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is GithubDeploySpec) {
            owner.convention(to.owner)
            repository.convention(to.repository)
        }
    }
}

open class GithubAuth @Inject constructor(objects: ObjectFactory) : Auth() {
    /** GitHub username. */
    val user: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }

    /** GitHub personal access token. */
    val token: Property<Secret> = objects.property<Secret>().apply { finalizeValueOnRead() }

    override fun fallback(to: Auth) {
        if (to is GithubAuth) {
            user.convention(to.user)
            token.convention(to.token)
        }
    }
}

internal open class GithubInitializationTask @Inject constructor(
    objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val layout: ProjectLayout
) : DefaultTask() {
    @get:Input val credentials: Property<PasswordCredentials> = objects.property()
    @get:Input val auth: Property<GithubAuth> = objects.property()

    @TaskAction fun execute() {
        val credentials = credentials.get()
        val auth = auth.get()
        credentials.username = auth.user.get().resolve(providers, layout, "spec.auth.user")
        credentials.password = auth.token.get().resolve(providers, layout, "spec.auth.token")
    }
}