@file:Suppress("UnstableApiUsage")

package io.deepmedia.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.deepmedia.tools.publisher.common.Release
import io.deepmedia.tools.publisher.common.Scm
import io.deepmedia.tools.publisher.github.GithubHandler
import io.deepmedia.tools.publisher.local.LocalHandler
import io.deepmedia.tools.publisher.sonatype.SonatypeHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension
import java.lang.IllegalArgumentException

open class PublisherPlugin : Plugin<Project> {

    private val handlers = mutableListOf<Handler<*>>()

    override fun apply(target: Project) {
        target.plugins.apply("maven-publish")
        target.plugins.apply("signing")

        handlers.add(LocalHandler(target))
        handlers.add(SonatypeHandler(target))
        handlers.add(GithubHandler(target))

        val task = target.tasks.register("publishAll")
        val extension = target.extensions.create("publisher", PublisherExtension::class.java)
        extension.publications = target.container(Publication::class.java) { name ->
            handlers.first { it.ownsPublication(name) }.createPublication(name)
        }
        extension.configuredPublications = target.container(Publication::class.java)
        extension.configuredPublications.all {
            val publication = this
            val handler = handlers.first { it.ownsPublication(publication.name) }
            handler as Handler<Publication>
            target.afterEvaluate { // For components to be created
                val default = extension as Publication
                fillPublication(target, publication, default, handler)
                checkPublication(target, publication, handler, fatal = false)
                val pubTask = createPublicationTask(target, publication, handler)
                task.dependsOn(pubTask)
            }
        }
    }

    private fun <P: Publication> fillPublication(
        target: Project,
        publication: P,
        default: Publication,
        handler: Handler<P>
    ) {
        publication.publication = publication.publication ?: default.publication
        publication.component = publication.component ?: default.component ?: when {
            target.isAndroidLibrary -> "release"
            target.isJava -> "java"
            publication.publication != null -> null // It's OK, we can still get the component from the publication
            else -> throw IllegalArgumentException("Project is not a java project, so we can't infer the component attribute.")
        }

        // Project data
        default.project.licenses.forEach(publication.project::addLicense)
        default.project.developers.forEach(publication.project::addDeveloper)

        publication.project.name = publication.project.name
            ?: default.project.name ?: target.rootProject.name
        publication.project.group = publication.project.group
            ?: default.project.group ?: target.group.toString()
        publication.project.artifact = publication.project.artifact
            ?: default.project.artifact
                    ?: target.extensions.findByType<BasePluginExtension>()?.archivesName?.orNull
        publication.project.url = publication.project.url ?: default.project.url
        publication.project.scm = publication.project.scm
            ?: default.project.scm
                    ?: publication.project.url?.let { Scm(it) }
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
        publication.release.tag = publication.release.tag
            ?: default.release.tag
                    ?: publication.release.vcsTag
                    ?: default.release.vcsTag
                    ?: "v${publication.release.version!!}"
        publication.release.description = publication.release.description
            ?: default.release.description ?: "${publication.project.name!!} ${publication.release.tag!!}"
        publication.release.sources = publication.release.sources ?: default.release.sources
        publication.release.docs = publication.release.docs ?: default.release.docs

        // Auto-sources and auto-docs support
        if (publication.release.sources == Release.SOURCES_AUTO) {
            publication.release.sources = target.registerSourcesTask(publication)
        }
        if (publication.release.docs == Release.DOCS_AUTO) {
            publication.release.docs = target.registerDocsTask(publication)
        }

        // Signing support
        publication.signing.key = target.findSecret(publication.signing.key ?: "signing.key")
        publication.signing.password = target.findSecret(publication.signing.password ?: "signing.password")

        // Give handler a chance
        handler.fillPublication(publication)
    }

    private fun <P: Publication> checkPublication(
        target: Project,
        publication: P,
        handler: Handler<P>,
        fatal: Boolean
    ) {
        handler.checkPublication(publication, fatal)
    }

    // https://developer.android.com/studio/build/maven-publish-plugin
    @Suppress("UnstableApiUsage")
    private fun <P: Publication> createPublicationTask(
        target: Project,
        publication: P,
        handler: Handler<P>
    ): TaskProvider<Task> {

        val mavenPublications = target.extensions.getByType(PublishingExtension::class.java).publications
        val mavenSourcePublication = publication.publication?.let { mavenPublications[it] as MavenPublication }
        val mavenPublication = when {
            mavenSourcePublication == null -> {
                // We have created the publication, so we must have a software component for it.
                mavenPublications.create(publication.name, MavenPublication::class) {
                    from(target.components[publication.component!!])
                }
            }
            publication.clonePublication -> {
                mavenPublications.create(publication.name, MavenPublication::class) {
                    // Cloning artifacts is tricky - from() will copy some of them, there might
                    // be more, and we can't allow duplicates.
                    mavenSourcePublication as MavenPublicationInternal
                    from(mavenSourcePublication.component!!)
                    mavenSourcePublication.artifacts.forEach {
                        val contained = artifacts.any { a ->
                            a.classifier == it.classifier && a.extension == it.extension
                        }
                        if (!contained) artifact(it)
                    }
                }
            }
            else -> mavenSourcePublication
        }

        configureMavenPublication(target, mavenPublication, publication)

        // Configure signing if present
        if (publication.signing.key != null || publication.signing.password != null) {
            val signing = target.extensions.getByType(SigningExtension::class)
            signing.useInMemoryPgpKeys(publication.signing.key, publication.signing.password)
            try {
                signing.sign(mavenPublication)
            } catch (e: Exception) {
                target.logger.log(LogLevel.WARN,
                    "Two or more publications share the same MavenPublication under the hood! " +
                            "Only one of the signatures will be used, and other configuration parameters " +
                            "might be conflicting as well.")
            }
        }

        val publishTask = handler.createPublicationTask(publication, mavenPublication)
        val checkTask = target.tasks.register("check${publication.name.capitalize()}") {
            doFirst { checkPublication(target, publication, handler, fatal = true) }
        }
        return target.tasks.register("publishTo${publication.name.capitalize()}") {
            dependsOn(checkTask)
            finalizedBy(publishTask)
        }
    }

    private fun configureMavenPublication(
        target: Project,
        maven: MavenPublication,
        publication: Publication
    ) {

        publication.release.sources?.let {
            // Add sources, but not if they are present already! Otherwise publishing will fail.
            val hasSources = maven.hasSources
            if (!hasSources) maven.artifact(it) else target.logger.log(LogLevel.WARN,
                "Couldn't add sources to ${maven.name}, because they are already present.")
        }
        publication.release.docs?.let {
            // Add docs, but not if they are present already! Otherwise publishing will fail.
            val hasDocs = maven.hasDocs
            if (!hasDocs) maven.artifact(it) else target.logger.log(LogLevel.WARN,
                "Couldn't add docs to ${maven.name}, because they are already present.")
        }
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
        maven.pom.developers {
            publication.project.developers.forEach {
                developer {
                    name.set(it.name)
                    email.set(it.email)
                    it.organization?.let { organization.set(it) }
                    it.url?.let { organizationUrl.set(it) }
                }
            }
        }
        maven.pom.scm {
            tag.set(publication.release.tag!!)
            publication.project.scm?.let {
                url.set(it.source(publication.release.tag!!))
                it.connection?.let { connection.set(it) }
                it.developerConnection?.let { developerConnection.set(it) }
            }
        }
    }
}
