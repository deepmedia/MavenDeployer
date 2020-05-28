package com.otaliastudios.tools.publisher.bintray

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import com.jfrog.bintray.gradle.tasks.BintrayPublishTask
import com.otaliastudios.tools.publisher.Publication
import com.otaliastudios.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.kotlin.dsl.delegateClosureOf
import java.util.*

internal class BintrayPublicationHandler : PublicationHandler() {

    companion object {
        internal const val PREFIX = "bintray"
    }

    override fun applyPlugins(target: Project) {
        target.plugins.apply("com.jfrog.bintray")
    }

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)

    override fun createPublication(name: String) = BintrayPublication(name)

    override fun fillPublication(target: Project, publication: Publication) {
        publication as BintrayPublication
        publication.auth.user = findSecret(target, publication.auth.user ?: "auth.repo")
        publication.auth.key = findSecret(target, publication.auth.key ?: "auth.repo")
        publication.auth.repo = findSecret(target, publication.auth.repo ?: "auth.repo")
    }

    override fun checkPublication(target: Project, publication: Publication) {
        publication as BintrayPublication
        // The only nullable and important fields at this point are auth* fields, but we want
        // to be tolerant on them as they might not be available e.g. on CI forks. Just warn.
        checkPublicationField(target, publication.auth.user, "auth.user", false)
        checkPublicationField(target, publication.auth.key, "auth.key", false)
        checkPublicationField(target, publication.auth.repo, "auth.repo", false)
    }

    override fun createPublicationTasks(target: Project, publication: Publication, mavenPublication: String): Iterable<String> {
        publication as BintrayPublication

        // I think the bintray plugin needs these three to work properly.
        val base = target.convention.getPlugin(BasePluginConvention::class.java)
        target.version = publication.release.version!!
        target.group = publication.project.group!!
        base.archivesBaseName = publication.project.artifact!!

        // Configure the plugin with the publication data.
        // We're replicating what ProjectsEvaluatedBuildListener.groovy does in the BGP
        val bintray = target.tasks.create("bintrayUpload${mavenPublication.capitalize()}", BintrayUploadTask::class.java)
        bintray.project = target
        bintray.setPublications(mavenPublication)
        bintray.apiUrl = "https://api.bintray.com" // BintrayUploadTask.API_URL_DEFAULT
        bintray.user = publication.auth.user ?: ""
        bintray.apiKey = publication.auth.key ?: ""
        bintray.override = true
        bintray.publish = true
        bintray.dryRun = publication.dryRun
        bintray.repoName = publication.auth.repo ?: ""
        bintray.packageName = publication.project.name
        publication.project.description?.let { bintray.packageDesc = it }
        publication.project.vcsUrl?.let { bintray.packageVcsUrl = it }
        val licenses = publication.project.licenses
        if (licenses.isNotEmpty()) {
            bintray.setPackageLicenses(*licenses.map { it.name }.toTypedArray())
        }
        bintray.versionName = publication.release.version!!
        bintray.versionDesc = publication.release.description!!
        bintray.versionReleased = Date().toString()
        bintray.versionVcsTag = publication.release.vcsTag!!
        bintray.dependsOn("publish${mavenPublication.capitalize()}PublicationToMavenLocal")

        // Need to call BintrayPublishTask.publishVersion to mark the version as published.
        // This is the actual bintrayPublish task that we'd call in the normal extension flow.
        val bintrayPublish = target.tasks.create("bintrayPublish${mavenPublication.capitalize()}", BintrayPublishTask::class.java)
        bintray.doLast {
            if (didWork) {
                bintrayPublishMethod.invoke(bintrayPublish,
                    bintray.repoName, bintray.packageName, bintray.versionName, bintray)
            }
        }

        return setOf(bintray.name)
    }


    private val bintrayPublishMethod by lazy {
        BintrayPublishTask::class.java.getDeclaredMethod("publishVersion",
            String::class.java,
            String::class.java,
            String::class.java,
            BintrayUploadTask::class.java).also {
            it.isAccessible = true
        }
    }
}