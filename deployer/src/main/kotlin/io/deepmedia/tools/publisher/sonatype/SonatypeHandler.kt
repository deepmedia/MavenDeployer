package io.deepmedia.tools.publisher.sonatype

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

    override fun checkPublication(publication: SonatypePublication, fatal: Boolean) {
        // Auth
        target.checkPublicationField(fatal, publication.auth.user, "sonatype.auth.user")
        target.checkPublicationField(fatal, publication.auth.password, "sonatype.auth.password")
        target.checkPublicationField(fatal, publication.signing.key, "signing.key")
        target.checkPublicationField(fatal, publication.signing.password, "signing.password")

        // Sources and docs
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        val maven = publication.publication?.let { publishing.publications[it] as? MavenPublication }
        val hasSources = publication.release.sources != null || (maven != null && maven.hasSources)
        val hasDocs = publication.release.docs != null || (maven != null && maven.hasDocs)
        target.checkPublicationCondition(fatal, hasSources, "release.sources") {
            "This field is required for sonatype publishing."
        }
        target.checkPublicationCondition(fatal, hasDocs, "release.docs") {
            "This field is required for sonatype publishing."
        }

        // Other
        target.checkPublicationField(fatal, publication.project.url, "project.url") {
            "This field is required for sonatype publishing."
        }
        target.checkPublicationCondition(fatal, publication.project.licenses.isNotEmpty(),
            "project.licenses") {
            "Sonatype requires at least one license in publisher.project.licenses."
        }
        target.checkPublicationCondition(fatal, publication.project.developers.isNotEmpty(),
            "project.developers") {
            "Sonatype requires at least one developer in publisher.project.developers."
        }
        /* publication.project.developers.forEach {
            require(it.organization != null && it.url != null) {
                "Sonatype requires all developers to have an organization and url."
            }
        } */
        target.checkPublicationField(fatal, publication.project.scm?.connection,
            "project.scm.connection") {
            "This field is required for sonatype publishing." +
                    "You can use utilities like GithubScm to set this for you."
        }
        target.checkPublicationField(fatal, publication.project.scm?.developerConnection,
            "project.scm.developerConnection") {
            "This field is required for sonatype publishing." +
                    "You can use utilities like GithubScm to set this for you."
        }
    }

    override fun createPublicationTask(
        publication: SonatypePublication,
        mavenPublication: MavenPublication
    ): String {
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
        return "publish${mavenPublication.name.capitalize()}PublicationTo${repository.capitalize()}Repository".also {
            allTask.dependsOn(it)
        }
    }

}