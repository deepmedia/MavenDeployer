@file:Suppress("UnstableApiUsage")

package io.deepmedia.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidBasePlugin
import io.deepmedia.tools.publisher.Publication
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getPlugin
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper


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

internal fun Project.registerDocsTask(publication: Publication): TaskProvider<Jar> {
    check(isKotlin) { "Release.DOCS_AUTO only works in kotlin projects." }
    apply<DokkaPlugin>()
    return tasks.register("generatePublisher${publication.name.capitalize()}Docs", Jar::class.java) {
        val task = tasks["dokkaJavadoc"] as DokkaTask
        dependsOn(task)
        archiveClassifier.set("javadoc")
        from(task.outputDirectory)
    }
}

internal val Project.isKotlin get() = plugins.toList().any { it is KotlinBasePluginWrapper }
internal val Project.isAndroid get() = plugins.toList().any { it is AndroidBasePlugin }
internal val Project.isAndroidLibrary get() = plugins.toList().any { it is LibraryPlugin }
internal val Project.isJava get() = plugins.toList().any { it is JavaBasePlugin }