package io.deepmedia.tools.publisher.local

import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.Publication
import io.deepmedia.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenSettingsProvider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

internal class LocalPublicationHandler(target: Project) : PublicationHandler<LocalPublication>(target) {

    companion object {
        internal const val PREFIX = "directory"
    }

    private val allTask = target.tasks.register("publishAll${PREFIX.capitalize()}")

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)

    override fun createPublication(name: String) = LocalPublication(name)

    override fun fillPublication(publication: LocalPublication) {
        // Not needed, done later.
        /** publication.directory = publication.directory ?: runCatching {
            val locations = DefaultMavenFileLocations()
            val settings = DefaultMavenSettingsProvider(locations)
            val locator = DefaultLocalMavenRepositoryLocator(settings)
            locator.localMavenRepository.absolutePath
        }.getOrNull() */
    }

    override fun checkPublication(publication: LocalPublication) {
        checkPublicationField(publication.directory, "directory", false)
    }

    override fun createPublicationTasks(publication: LocalPublication, mavenPublication: MavenPublication): Iterable<String> {
        val mavenRepository = publication.name // whatever
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        publishing.repositories {
            if (publication.directory != null) {
                maven {
                    this.setUrl(publication.directory!!)
                    this.name = mavenRepository
                }
            } else {
                mavenLocal {
                    this.name = mavenRepository
                }
            }
        }
        val publishTask = "publish${mavenPublication.name.capitalize()}PublicationTo${mavenRepository.capitalize()}Repository"
        allTask.dependsOn(publishTask)
        return setOf(publishTask)
    }
}