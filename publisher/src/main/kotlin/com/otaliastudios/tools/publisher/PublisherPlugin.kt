@file:Suppress("UnstableApiUsage")

package com.otaliastudios.tools.publisher

import com.android.build.gradle.BaseExtension
import com.otaliastudios.tools.publisher.bintray.BintrayPublicationHandler
import com.otaliastudios.tools.publisher.common.Release
import com.otaliastudios.tools.publisher.local.LocalPublicationHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.lang.IllegalArgumentException

open class PublisherPlugin : Plugin<Project> {

    private val handlers = listOf(
        BintrayPublicationHandler(),
        LocalPublicationHandler()
    )

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        handlers.forEach { it.applyPlugins(target) }

        val extension = target.extensions.create("publisher", PublisherExtension::class.java)
        extension.publications = target.container(Publication::class.java) { name ->
            handlers.first { it.ownsPublication(name) }.createPublication(name)
        }

        target.afterEvaluate {
            val publications = extension.publications
            val default = extension as Publication
            val tasks = publications.map { publication ->
                val handler = handlers.first { it.ownsPublication(publication.name) }
                fillPublication(target, publication, default, handler)
                checkPublication(target, publication, handler)
                createPublicationTask(target, publication, handler)
            }
            target.tasks.register("publishAll") {
                dependsOn(*tasks.toTypedArray())
            }
        }
    }

    private fun fillPublication(target: Project, publication: Publication, default: Publication, handler: PublicationHandler) {
        publication.component = publication.component ?: default.component ?: when {
            target.isAndroidLibrary -> "release"
            target.isJava -> "java"
            else -> throw IllegalArgumentException("Project is not a java project, so we can't infer the component attribute.")
        }

        // Project data
        publication.project.name = publication.project.name
            ?: default.project.name ?: target.rootProject.name
        publication.project.group = publication.project.group
            ?: default.project.group ?: target.group.toString()
        val base = target.convention.getPlugin(BasePluginConvention::class.java)
        publication.project.artifact = publication.project.artifact
            ?: default.project.artifact ?: base.archivesBaseName
        publication.project.vcsUrl = publication.project.vcsUrl
            ?: default.project.vcsUrl ?: publication.project.url ?: default.project.url
        publication.project.packaging = publication.project.packaging
            ?: default.project.packaging ?: if (target.isAndroidLibrary) "aar" else null

        // Release data
        publication.release.version = publication.release.version
            ?: default.release.version ?: if (target.isAndroid) {
                val android = target.extensions.getByName("android") as BaseExtension
                android.defaultConfig.versionName + (android.defaultConfig.versionNameSuffix ?: "")
            } else {
                target.version.toString()
            }
        publication.release.vcsTag = publication.release.vcsTag
            ?: default.release.vcsTag ?: "v${publication.release.version!!}"
        publication.release.description = publication.release.description
            ?: default.release.description ?: "${publication.project.name!!} ${publication.release.vcsTag!!}"
        publication.release.sources = publication.release.sources ?: default.release.sources
        publication.release.docs = publication.release.docs ?: default.release.docs

        // Auto-sources and auto-docs support
        if (publication.release.sources == Release.SOURCES_AUTO) {
            publication.release.setSources(target.createSources(publication))
        }
        if (publication.release.docs == Release.DOCS_AUTO) {
            publication.release.setDocs(target.createDocs(publication))
        }

        // Give handler a chance
        handler.fillPublication(target, publication)
    }

    private fun checkPublication(target: Project, publication: Publication, handler: PublicationHandler) {
        handler.checkPublication(target, publication)
    }

    // https://developer.android.com/studio/build/maven-publish-plugin
    @Suppress("UnstableApiUsage")
    private fun createPublicationTask(target: Project, publication: Publication, handler: PublicationHandler): TaskProvider<Task> {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            register(publication.name, MavenPublication::class) {
                from(target.components[publication.component!!])
                publication.release.sources?.let { artifact(it) }
                publication.release.docs?.let { artifact(it) }
                groupId = publication.project.group!!
                artifactId = publication.project.artifact!!
                version = publication.release.version!!
                publication.project.packaging?.let { pom.packaging = it }
                publication.project.description?.let { pom.description.set(it) }
                publication.project.url?.let { pom.url.set(it) }
                pom.name.set(publication.project.name!!)
                pom.licenses {
                    publication.project.licenses.forEach {
                        license {
                            name.set(it.name)
                            url.set(it.url)
                        }
                    }
                }
                pom.scm {
                    publication.project.vcsUrl?.let { connection.set(it) }
                    publication.project.vcsUrl?.let { developerConnection.set(it) }
                    publication.project.url?.let { url.set(it) }
                    publication.release.vcsTag?.let { tag.set(it) }
                }
            }
        }

        val tasks = handler.createPublicationTasks(target, publication)
        return target.tasks.register("publishTo${publication.name}") {
            dependsOn(*tasks.toList().toTypedArray())
        }
    }
}
