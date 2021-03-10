@file:Suppress("UnstableApiUsage")

package io.deepmedia.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidBasePlugin
import io.deepmedia.tools.publisher.Publication
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getPlugin
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal fun Project.checkPublicationField(fatal: Boolean, value: Any?, field: String, message: () -> String = { "" }) {
    if (fatal) {
        requireNotNull(value) { "publisher.$field is not set. ${message()}" }
    } else if (value == null) {
        logger.log(LogLevel.WARN, "publisher.$field is not set. ${message()}" )
    }
}

internal fun Project.checkPublicationCondition(fatal: Boolean, condition: Boolean, field: String, message: () -> String = { "" }) {
    if (fatal) {
        require(condition) { "publisher.$field is not properly set. ${message()}" }
    } else if (!condition) {
        logger.log(LogLevel.WARN, "publisher.$field is not properly set. ${message()}" )
    }
}

private val localPropertiesCache = ConcurrentHashMap<Project, Properties>()

internal fun Project.findSecret(key: String): String? {
    // Try with environmental variable.
    val env: String? = System.getenv(key)
    if (!env.isNullOrEmpty()) return env
    // Try with findProperty.
    val project = findProperty(key) as? String
    if (!project.isNullOrEmpty()) return project
    // Try with local.properties file.
    val properties = localPropertiesCache.getOrPut(rootProject) {
        val properties = Properties()
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            val stream = FileInputStream(file)
            properties.load(stream)
        }
        properties
    }
    val local = properties!!.getProperty(key)
    if (!local.isNullOrEmpty()) return local
    // We failed. Return null.
    return null
}

internal val MavenArtifact.isSources get() = classifier == "sources" && extension == "jar"
internal val MavenPublication.hasSources get() = artifacts.any(MavenArtifact::isSources)

internal fun Project.registerSourcesTask(publication: Publication): TaskProvider<Jar> {
    check(isJava) { "Release.SOURCES_AUTO only works with Java projects." }
    val project = this
    return tasks.register("generatePublisher${publication.name.capitalize()}Sources", Jar::class.java) {
        archiveClassifier.set("sources")
        if (isAndroid) {
            val android = project.extensions["android"] as BaseExtension
            from(android.sourceSets["main"].java.srcDirs)
        } else {
            val convention: JavaPluginConvention = project.convention.getPlugin()
            val sourceSet = convention.sourceSets["main"]
            from(sourceSet.allSource)
            // or: val sourceSets = project.extensions["sourceSets"] as SourceSetContainer
            // but this is safer.
        }
    }
}

internal val MavenArtifact.isDocs get() = classifier == "javadoc" && extension == "jar"
internal val MavenPublication.hasDocs get() = artifacts.any(MavenArtifact::isDocs)

internal fun Project.registerDocsTask(publication: Publication): TaskProvider<Jar> {
    check(isKotlin) { "Release.DOCS_AUTO only works in kotlin projects." }
    apply<DokkaPlugin>()
    return tasks.register("generatePublisher${publication.name.capitalize()}Docs", Jar::class.java) {
        val taskName = if (isKotlinMultiplatform) "dokkaHtml" else "dokkaJavadoc"
        val task = tasks[taskName] as DokkaTask
        dependsOn(task)
        archiveClassifier.set("javadoc")
        from(task.outputDirectory)
    }
}

internal val Project.isKotlinMultiplatform get() = plugins.toList().any { it is KotlinMultiplatformPluginWrapper }
internal val Project.isKotlin get() = plugins.toList().any { it is KotlinBasePluginWrapper }
internal val Project.isAndroid get() = plugins.toList().any { it is AndroidBasePlugin }
internal val Project.isAndroidLibrary get() = plugins.toList().any { it is LibraryPlugin }
internal val Project.isJava get() = plugins.toList().any { it is JavaBasePlugin }