package com.otaliastudios.tools.publisher.local

import com.otaliastudios.tools.publisher.Publication
import com.otaliastudios.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

internal class LocalPublicationHandler : PublicationHandler() {

    companion object {
        internal const val PREFIX = "directory"
    }

    override fun applyPlugins(target: Project) = Unit
    override fun ownsPublication(name: String) = name.startsWith(PREFIX)
    override fun createPublication(name: String) = LocalPublication(name)
    override fun fillPublication(target: Project, publication: Publication) = Unit

    override fun checkPublication(target: Project, publication: Publication) {
        publication as LocalPublication
        checkPublicationField(target, publication.directory, "directory", false)
    }

    override fun createPublicationTasks(target: Project, publication: Publication): Iterable<String> {
        publication as LocalPublication
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        publishing.repositories {
            if (publication.directory != null) {
                maven {
                    this.setUrl(publication.directory!!)
                    this.name = publication.name
                }
            } else {
                mavenLocal {
                    this.name = publication.name
                }
            }
        }
        return setOf("publish${publication.name.capitalize()}PublicationTo${publication.name.capitalize()}Repository")
    }
}