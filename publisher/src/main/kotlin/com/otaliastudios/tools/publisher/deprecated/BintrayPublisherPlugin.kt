package com.otaliastudios.tools.publisher.deprecated

import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.PluginContainer
import org.gradle.kotlin.dsl.delegateClosureOf
import java.util.*

class BintrayPublisherPlugin : PublisherPlugin<BintrayPublisherExtension>("bintray") {

    override val modelClass = BintrayPublisherExtension::class
    override val uniqueExtensionName = "bintrayPublisher"

    override fun addPlugins(plugins: PluginContainer) {
        super.addPlugins(plugins)
        plugins.apply("com.jfrog.bintray")
    }

    override fun fillModel(target: Project, model: BintrayPublisherExtension) {
        super.fillModel(target, model)
        model.auth.user = findSecret(target, model.auth.user ?: "auth.repo")
        model.auth.key = findSecret(target, model.auth.key ?: "auth.repo")
        model.auth.repo = findSecret(target, model.auth.repo ?: "auth.repo")
    }

    override fun checkModel(target: Project, model: BintrayPublisherExtension) {
        super.checkModel(target, model)
        // The only nullable and important fields at this point are auth* fields, but we want
        // to be tolerant on them as they might not be available e.g. on CI forks. Just warn.
        checkModelField(target, model.auth.user, "auth.user", false)
        checkModelField(target, model.auth.key, "auth.key", false)
        checkModelField(target, model.auth.repo, "auth.repo", false)
    }

    override fun createPublication(target: Project, model: BintrayPublisherExtension) {
        super.createPublication(target, model)

        // I think the bintray plugin needs these three to work properly.
        val base = target.convention.getPlugin(BasePluginConvention::class.java)
        target.version = model.release.version!!
        target.group = model.project.group!!
        base.archivesBaseName = model.project.artifact!!

        // Configure the plugin with the publication data.
        val bintray = target.extensions.getByType(BintrayExtension::class.java)
        bintray.setPublications(model.publication)
        bintray.user = model.auth.user ?: ""
        bintray.key = model.auth.key ?: ""
        bintray.override = true
        bintray.publish = true
        bintray.pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = model.auth.repo ?: ""
            name = model.project.name
            model.project.description?.let { desc = it }
            model.project.vcsUrl?.let { vcsUrl = it }
            val licenses = model.project.licenses
            if (licenses.isNotEmpty()) {
                setLicenses(*licenses.map { it.name }.toTypedArray())
            }
            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                name = model.release.version!!
                desc = model.release.description!!
                released = Date().toString()
                vcsTag = model.release.vcsTag!!
            })
        })
    }

    override fun createPublishingTasks(target: Project, model: BintrayPublisherExtension) {
        val name = "publishTo${model.publication!!.capitalize()}"
        target.tasks.create(name) {
            dependsOn("bintrayUpload")
        }
    }
}