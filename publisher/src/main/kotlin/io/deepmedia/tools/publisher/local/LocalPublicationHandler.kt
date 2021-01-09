package io.deepmedia.tools.publisher.local

import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.Publication
import io.deepmedia.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

internal class LocalPublicationHandler(target: Project) : PublicationHandler(target) {

    companion object {
        internal const val PREFIX = "directory"
    }

    private val allTask = target.tasks.register("publishAll${PREFIX.capitalize()}")

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)
    override fun createPublication(name: String) = LocalPublication(name)
    override fun fillPublication(publication: Publication) = Unit

    override fun checkPublication(publication: Publication) {
        publication as LocalPublication
        checkPublicationField(publication.directory, "directory", false)
    }

    override fun createPublicationTasks(publication: Publication, mavenPublication: MavenPublication): Iterable<String> {
        publication as LocalPublication
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