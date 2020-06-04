package com.otaliastudios.tools.publisher.local

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.otaliastudios.tools.publisher.Publication
import com.otaliastudios.tools.publisher.PublicationHandler
import com.otaliastudios.tools.publisher.bintray.BintrayPublicationHandler
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

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

    override fun createPublicationTasks(publication: Publication, mavenPublication: String): Iterable<String> {
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
        val publishTask = "publish${mavenPublication.capitalize()}PublicationTo${mavenRepository.capitalize()}Repository"
        allTask.dependsOn(publishTask)
        return setOf(publishTask)
    }
}