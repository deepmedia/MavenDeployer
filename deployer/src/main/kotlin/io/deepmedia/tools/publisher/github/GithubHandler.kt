package io.deepmedia.tools.publisher.github

import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.*
import io.deepmedia.tools.publisher.Handler
import io.deepmedia.tools.publisher.checkPublicationCondition
import io.deepmedia.tools.publisher.checkPublicationField
import io.deepmedia.tools.publisher.findSecret
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType

internal class GithubHandler(target: Project) : Handler<GithubPublication>(target) {

    companion object {
        internal const val PREFIX = "github"
    }

    private val allTask = target.tasks.register("publishAll${PREFIX.capitalize()}")

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)

    override fun createPublication(name: String) = GithubPublication(name)

    override fun fillPublication(publication: GithubPublication) {
        publication.auth.user = target.findSecret(publication.auth.user ?: "auth.user")
        publication.auth.token = target.findSecret(publication.auth.token ?: "auth.token")
        publication.owner = publication.owner ?: publication.auth.user
        publication.repository = publication.repository ?: target.rootProject.name.takeIf { it.isNotBlank() }
    }

    override fun checkPublication(publication: GithubPublication, fatal: Boolean) {
        target.checkPublicationField(fatal, publication.auth.user, "github.auth.user")
        target.checkPublicationField(fatal, publication.auth.token, "github.auth.token")
        target.checkPublicationField(fatal, publication.repository, "github.repository")
        target.checkPublicationField(fatal, publication.owner, "github.owner")
    }

    override fun createPublicationTask(
        publication: GithubPublication,
        mavenPublication: MavenPublication
    ): String {
        val repository = publication.name // whatever
        val publishing = target.extensions.getByType(PublishingExtension::class)
        publishing.repositories {
            maven {
                this.name = repository
                this.url = target.uri("https://maven.pkg.github.com/${publication.owner}/${publication.repository}")
                credentials.password = publication.auth.token
                credentials.username = publication.auth.user
            }
        }
        return "publish${mavenPublication.name.capitalize()}PublicationTo${repository.capitalize()}Repository".also {
            allTask.dependsOn(it)
        }
    }

}