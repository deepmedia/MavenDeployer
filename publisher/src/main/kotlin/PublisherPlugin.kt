package com.otaliastudios.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import java.io.FileInputStream
import java.util.*
import kotlin.IllegalArgumentException
import kotlin.reflect.KClass

abstract class PublisherPlugin<M : PublisherExtension>(
        private val defaultPublication: String
) : Plugin<Project> {

    abstract val modelClass: KClass<M>

    private var localProperties: Properties? = null

    @Suppress("ControlFlowWithEmptyBody")
    override fun apply(target: Project) {
        addPlugins(target.plugins)
        val model = target.extensions.create("publisher", modelClass.java)

        // Wait for evaluation before going on, as for instance components
        // are only created in the evaluation phase.
        target.afterEvaluate {
            // Fill model with data.
            fillModel(target, model)
            checkModel(target, model)

            // Create a maven publication.
            if (target.isAndroidLibrary) {
                createPublication(target, model, "release")
            } else if (!target.isAndroid) {
                createPublication(target, model, "java")
            } else {
                // Not an android library, but has some android plugin...
                // Do nothing for now.
            }
            // Create publishing tasks.
            createPublishingTasks(target, model)
        }
    }

    protected open fun addPlugins(plugins: PluginContainer) {
        plugins.apply("maven-publish")
    }

    protected open fun fillModel(target: Project, model: M) {
        // Add defaults
        model.publication = model.publication ?: defaultPublication
        model.project.name = model.project.name ?: target.rootProject.name
        model.project.group = model.project.group ?: target.group.toString()
        val base = target.convention.getPlugin(BasePluginConvention::class.java)
        model.project.artifact = model.project.artifact ?: base.archivesBaseName
        model.project.vcsUrl = model.project.vcsUrl ?: model.project.url
        if (model.project.packaging == null && target.isAndroidLibrary) {
            model.project.packaging = "aar"
        }
        model.release.version = model.release.version ?:
            if (target.isAndroid) {
                val android = target.extensions.getByName("android") as BaseExtension
                android.defaultConfig.versionName + android.defaultConfig.versionNameSuffix
            } else {
                target.version.toString()
            }
        model.release.vcsTag = model.release.vcsTag ?: "v${model.release.version!!}"
        model.release.description = model.release.description ?:
                "${model.project.name!!} ${model.release.vcsTag!!}"

        // Auto-sources and auto-docs support
        if (model.release.sources == PublisherExtension.Release.SOURCES_AUTO) {
            model.release.setSources(createSourcesJar(target))
        }
        if (model.release.docs == PublisherExtension.Release.DOCS_AUTO) {
            model.release.setDocs(if (target.isKotlin) {
                createDocsKotlinJar(target)
            } else {
                createDocsJavaJar(target)
            })
        }
    }

    protected open fun checkModel(target: Project, model: M) {
        // Check consistency.
    }

    @Suppress("SameParameterValue")
    protected fun checkModelField(target: Project, value: Any?, field: String, fatal: Boolean) {
        if (value == null) {
            val message = "publisher.$field is not set."
            if (fatal) {
                throw IllegalArgumentException(message)
            } else {
                target.logger.log(LogLevel.WARN, message)
            }
        }
    }

    // https://developer.android.com/studio/build/maven-publish-plugin
    @Suppress("UnstableApiUsage")
    protected open fun createPublication(target: Project, model: M, component: String) {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)
        publishing.publications {
            register(model.publication!!, MavenPublication::class) {
                from(target.components[component])
                model.release.sources?.let { artifact(it) }
                model.release.docs?.let { artifact(it) }
                groupId = model.project.group!!
                artifactId = model.project.artifact!!
                version = model.release.version!!
                model.project.packaging?.let { pom.packaging = it }
                model.project.description?.let { pom.description.set(it) }
                model.project.url?.let { pom.url.set(it) }
                pom.name.set(model.project.name!!)
                pom.licenses {
                    model.project.licenses.forEach {
                        license {
                            name.set(it.name)
                            url.set(it.url)
                        }
                    }
                }
                pom.scm {
                    model.project.vcsUrl?.let { connection.set(it) }
                    model.project.vcsUrl?.let { developerConnection.set(it) }
                    model.project.url?.let { url.set(it) }
                    model.release.vcsTag?.let { tag.set(it) }
                }
            }
        }
    }

    protected abstract fun createPublishingTasks(target: Project, model: M)

    protected fun findSecret(target: Project, key: String): String? {
        // Try with environmental variable.
        val env: String? = System.getenv(key)
        if (!env.isNullOrEmpty()) return env
        // Try with findProperty.
        val project = target.findProperty(key) as? String
        if (!project.isNullOrEmpty()) return project
        // Try with local.properties file.
        if (localProperties == null) {
            val properties = Properties()
            val file = target.rootProject.file("local.properties")
            if (file.exists()) {
                val stream = FileInputStream(file)
                properties.load(stream)
            }
            localProperties = properties
        }
        val local = localProperties!!.getProperty(key)
        if (!local.isNullOrEmpty()) return local
        // We failed. Return null.
        return null
    }

    private fun createSourcesJar(target: Project): Jar {
        return target.tasks.create("autoSourcesTask", Jar::class.java) {
            archiveClassifier.set("sources")
            if (target.isAndroid) {
                val android = target.extensions.getByName("android") as BaseExtension
                from(android.sourceSets["main"].java.srcDirs)
            } else {
                val sourceSets = target.extensions.getByName("sourceSets") as SourceSetContainer
                from(sourceSets["main"].allSource)
            }
        }
    }

    private fun createDocsKotlinJar(target: Project): Jar {
        target.apply<DokkaPlugin>()
        return target.tasks.create("autoDocsTask", Jar::class.java) {
            val task = target.tasks["dokka"]
            dependsOn(task)
            archiveClassifier.set("javadoc")
            from((task as DokkaTask).outputDirectory)
        }
    }

    private fun createDocsJavaJar(target: Project): Jar {
        throw IllegalArgumentException("Release.DOCS_AUTO only works in kotlin projects.")
    }
}

val Project.isKotlin get() = plugins.toList().any { it is KotlinBasePluginWrapper }
val Project.isAndroid get() = plugins.toList().any { it is AndroidBasePlugin }
val Project.isAndroidLibrary get() = plugins.toList().any { it is LibraryPlugin }