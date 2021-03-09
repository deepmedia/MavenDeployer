package io.deepmedia.tools.publisher.sonatype

import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.Handler
import io.deepmedia.tools.publisher.findSecret
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension

internal class SonatypeHandler(target: Project) : Handler<SonatypePublication>(target) {

    companion object {
        internal const val PREFIX = "sonatype"
    }

    private val allTask = target.tasks.register("publishAll${PREFIX.capitalize()}")

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)

    override fun createPublication(name: String) = SonatypePublication(name)

    /**
     * See https://central.sonatype.org/pages/requirements.html . In short:
     * - pom.groupId        => This is guaranteed to be present
     * - pom.artifactId     => This is guaranteed to be present
     * - pom.version        => This is guaranteed to be present
     * - pom.packaging required unless jar. This is implicit
     * - pom.name           => This is guaranteed to be present
     * - pom.description    => We can default to the project name
     * - pom.url            => We throw if absent
     * - at least 1 license => We throw if absent
     * - at least 1 dev     => We throw if absent
     * - sources jar        => We throw if absent
     * - docs jar           => We throw if absent
     * - complete scm       => We throw if incomplete
     */
    override fun fillPublication(publication: SonatypePublication) {
        publication.project.description = publication.project.description ?: publication.project.name
        publication.auth.user = target.findSecret(publication.auth.user ?: "auth.user")
        publication.auth.password = target.findSecret(publication.auth.password ?: "auth.password")
    }

    override fun checkPublication(publication: SonatypePublication) {
        // Auth
        checkPublicationField(publication.auth.user, "auth.user", true)
        checkPublicationField(publication.auth.password, "auth.password", true)
        checkPublicationField(publication.signing.key, "signing.key", true)
        checkPublicationField(publication.signing.password, "signing.password", true)

        // Other
        checkPublicationField(publication.release.sources, "release.sources", true,
            "This field is required for sonatype publishing.")
        checkPublicationField(publication.release.docs, "release.docs", true,
            "This field is required for sonatype publishing.")
        checkPublicationField(publication.project.url, "project.url", true,
            "This field is required for sonatype publishing.")
        require(publication.project.licenses.isNotEmpty()) {
            "Sonatype requires at least one license in publisher.project.licenses."
        }
        require(publication.project.developers.isNotEmpty()) {
            "Sonatype requires at least one developer in publisher.project.developers."
        }
        publication.project.developers.forEach {
            require(it.organization != null && it.url != null) {
                "Sonatype requires all developers to have an organization and url."
            }
        }
        checkPublicationField(publication.project.scm?.connection,
            "project.scm.connection", true,
            "This field is required for sonatype publishing. " +
                    "You can use utilities like GithubScm to set this for you.")
        checkPublicationField(publication.project.scm?.developerConnection,
            "project.scm.developerConnection", true,
            "This field is required for sonatype publishing. " +
                    "You can use utilities like GithubScm to set this for you.")
    }

    override fun createPublicationTasks(
        publication: SonatypePublication,
        mavenPublication: MavenPublication
    ): Iterable<String> {
        val repository = publication.name // whatever
        val publishing = target.extensions.getByType(PublishingExtension::class)
        publishing.repositories {
            maven {
                this.name = repository
                setUrl(publication.repository)
                credentials.password = publication.auth.password
                credentials.username = publication.auth.user
            }
        }
        val publishTask = "publish${mavenPublication.name.capitalize()}PublicationTo${repository.capitalize()}Repository"
        allTask.dependsOn(publishTask)
        return setOf(publishTask)
    }

}