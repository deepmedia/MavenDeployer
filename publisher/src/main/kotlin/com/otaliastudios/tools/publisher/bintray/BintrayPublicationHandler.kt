package com.otaliastudios.tools.publisher.bintray

import com.jfrog.bintray.gradle.BintrayExtension
import com.otaliastudios.tools.publisher.Publication
import com.otaliastudios.tools.publisher.PublicationHandler
import org.gradle.api.Project
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
        val bintray = target.extensions.getByType(BintrayExtension::class.java)
        bintray.setPublications(mavenPublication)
        bintray.user = publication.auth.user ?: ""
        bintray.key = publication.auth.key ?: ""
        bintray.override = true
        bintray.publish = true
        bintray.dryRun = publication.dryRun
        bintray.pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = publication.auth.repo ?: ""
            name = publication.project.name
            publication.project.description?.let { desc = it }
            publication.project.vcsUrl?.let { vcsUrl = it }
            val licenses = publication.project.licenses
            if (licenses.isNotEmpty()) {
                setLicenses(*licenses.map { it.name }.toTypedArray())
            }
            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                name = publication.release.version!!
                desc = publication.release.description!!
                released = Date().toString()
                vcsTag = publication.release.vcsTag!!
            })
        })
        return setOf("bintrayUpload")
    }
}