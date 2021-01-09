@file:Suppress("UnstableApiUsage")

package io.deepmedia.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.bintray.BintrayPublicationHandler
import io.deepmedia.tools.publisher.common.Release
import io.deepmedia.tools.publisher.local.LocalPublicationHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import java.lang.IllegalArgumentException

open class PublisherPlugin : Plugin<Project> {

    private val handlers = mutableListOf<PublicationHandler>()

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        handlers.add(BintrayPublicationHandler(target))
        handlers.add(LocalPublicationHandler(target))

        val task = target.tasks.register("publishAll")
        val extension = target.extensions.create("publisher", PublisherExtension::class.java)
        extension.publications = target.container(Publication::class.java) { name ->
            handlers.first { it.ownsPublication(name) }.createPublication(name)
        }
        extension.configuredPublications = target.container(Publication::class.java)
        extension.configuredPublications.all {
            val publication = this
            val handler = handlers.first { it.ownsPublication(publication.name) }
            target.afterEvaluate { // For components to be created
                val default = extension as Publication
                fillPublication(target, publication, default, handler)
                checkPublication(target, publication, handler)
                val pubTask = createPublicationTask(target, publication, handler)
                task.dependsOn(pubTask)
            }
        }
    }

    private fun fillPublication(target: Project, publication: Publication, default: Publication, handler: PublicationHandler) {
        publication.publication = publication.publication ?: default.publication
        publication.component = publication.component ?: default.component ?: when {
            target.isAndroidLibrary -> "release"
            target.isJava -> "java"
            publication.publication != null -> null // It's OK, we can still get the component from the publication
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
            ?: default.project.packaging ?: when {
                publication.publication != null -> null // we'll use the MavenPublication packaging
                target.isAndroidLibrary -> "aar"
                else -> null
            }

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
            publication.release.sources = target.registerSourcesTask(publication)
        }
        if (publication.release.docs == Release.DOCS_AUTO) {
            publication.release.docs = target.registerDocsTask(publication)
        }

        // Give handler a chance
        handler.fillPublication(publication)
    }

    private fun checkPublication(target: Project, publication: Publication, handler: PublicationHandler) {
        handler.checkPublication(publication)
    }

    // https://developer.android.com/studio/build/maven-publish-plugin
    @Suppress("UnstableApiUsage")
    private fun createPublicationTask(target: Project, publication: Publication, handler: PublicationHandler): TaskProvider<Task> {
        var mavenPublication: MavenPublication? = null
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            val container = this
            if (publication.publication != null) {
                mavenPublication = container[publication.publication!!] as MavenPublication
                configureMavenPublication(target, mavenPublication!!, publication, owned = false)
            } else {
                mavenPublication = container.create(publication.name, MavenPublication::class) {
                    configureMavenPublication(target, this, publication, owned = true)
                }
            }
        }

        val tasks = handler.createPublicationTasks(publication, mavenPublication!!)
        return target.tasks.register("publishTo${publication.name.capitalize()}") {
            dependsOn(*tasks.toList().toTypedArray())
        }
    }

    private fun configureMavenPublication(target: Project, maven: MavenPublication, publication: Publication, owned: Boolean) {
        if (owned) {
            // We have created the publication, so we must have a software component for it.
            maven.from(target.components[publication.component!!])
        }
        publication.release.sources?.let { maven.artifact(it) }
        publication.release.docs?.let { maven.artifact(it) }
        maven.groupId = publication.project.group!!
        maven.artifactId = publication.project.artifact!!
        maven.version = publication.release.version!!
        publication.project.packaging?.let { maven.pom.packaging = it }
        publication.project.description?.let { maven.pom.description.set(it) }
        publication.project.url?.let { maven.pom.url.set(it) }
        maven.pom.name.set(publication.project.name!!)
        maven.pom.licenses {
            publication.project.licenses.forEach {
                license {
                    name.set(it.name)
                    url.set(it.url)
                }
            }
        }
        maven.pom.scm {
            publication.project.vcsUrl?.let { connection.set(it) }
            publication.project.vcsUrl?.let { developerConnection.set(it) }
            publication.project.url?.let { url.set(it) }
            publication.release.vcsTag?.let { tag.set(it) }
        }
    }
}
