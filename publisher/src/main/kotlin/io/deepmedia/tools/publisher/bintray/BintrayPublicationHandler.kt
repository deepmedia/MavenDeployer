package io.deepmedia.tools.publisher.bintray

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import com.jfrog.bintray.gradle.tasks.BintrayPublishTask
import io.deepmedia.tools.publisher.Publication
import io.deepmedia.tools.publisher.PublicationHandler
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import java.util.*

internal class BintrayPublicationHandler(target: Project) : PublicationHandler<BintrayPublication>(target) {

    companion object {
        internal const val PREFIX = "bintray"
    }

    private val allTask = target.tasks.register("publishAll${PREFIX.capitalize()}")

    init {
        target.plugins.apply("com.jfrog.bintray")
    }

    override fun ownsPublication(name: String) = name.startsWith(PREFIX)

    override fun createPublication(name: String) = BintrayPublication(name)

    override fun fillPublication(publication: BintrayPublication) {
        publication.auth.user = findSecret(publication.auth.user ?: "auth.repo")
        publication.auth.key = findSecret(publication.auth.key ?: "auth.repo")
        publication.auth.repo = findSecret(publication.auth.repo ?: "auth.repo")
    }

    override fun checkPublication(publication: BintrayPublication) {
        // The only nullable and important fields at this point are auth* fields, but we want
        // to be tolerant on them as they might not be available e.g. on CI forks. Just warn.
        checkPublicationField(publication.auth.user, "auth.user", false)
        checkPublicationField(publication.auth.key, "auth.key", false)
        checkPublicationField(publication.auth.repo, "auth.repo", false)
    }

    override fun createPublicationTasks(publication: BintrayPublication, mavenPublication: MavenPublication): Iterable<String> {
        // I think the bintray plugin needs these three to work properly.
        val base = target.convention.getPlugin(BasePluginConvention::class.java)
        target.version = publication.release.version!!
        target.group = publication.project.group!!
        base.archivesBaseName = publication.project.artifact!!

        // Hack for gradle module metadata, since bintray plugin is dumb
        // This is a problem because we'll have a duplicate artifact for other publishers
        mavenPublication as MavenPublicationInternal
        val gradleModuleMetadata = mavenPublication.publishableArtifacts.find { it.extension == "module" }
        if (gradleModuleMetadata != null) {
            target.logger.log(LogLevel.WARN, "Found gradle module metadata. Registering it as an artifact for Bintray upload.")
            mavenPublication.artifact(gradleModuleMetadata)
        }

        // Configure the plugin with the publication data.
        // We're replicating what ProjectsEvaluatedBuildListener.groovy does in the BGP
        val bintray = target.tasks.register(
            "bintrayUpload${mavenPublication.name.capitalize()}",
            BintrayUploadTask::class.java) {
            val bintray = this
            bintray.project = target
            bintray.setPublications(mavenPublication.name)
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
            bintray.dependsOn("publish${mavenPublication.name.capitalize()}PublicationToMavenLocal")
        }


        // Need to call BintrayPublishTask.publishVersion to mark the version as published.
        // This is the actual bintrayPublish task that we'd call in the normal extension flow.
        val bintrayPublish = target.tasks.register(
            "bintrayPublish${mavenPublication.name.capitalize()}",
            BintrayPublishTask::class.java)
        bintray.configure {
            val bintray = this
            doLast {
                if (didWork) {
                    bintrayPublishMethod.invoke(bintrayPublish.get(),
                        bintray.repoName, bintray.packageName, bintray.versionName, bintray)
                }
            }
        }

        allTask.dependsOn(bintray.name)
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