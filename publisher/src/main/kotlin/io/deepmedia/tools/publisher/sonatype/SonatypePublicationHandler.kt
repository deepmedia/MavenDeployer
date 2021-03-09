package io.deepmedia.tools.publisher.sonatype

import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.Publication
import io.deepmedia.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

internal class SonatypePublicationHandler(target: Project) : PublicationHandler<SonatypePublication>(target) {

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
     */
    override fun fillPublication(publication: SonatypePublication) {
        publication.project.description = publication.project.description ?: publication.project.name
    }

    override fun checkPublication(publication: SonatypePublication) {
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


    }

}