@file:Suppress("UnstableApiUsage")

package com.otaliastudios.tools.publisher

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper


internal fun Project.createSources(publication: Publication): Jar {
    return tasks.create("generatePublisher${publication.name.capitalize()}Sources", Jar::class.java) {
        archiveClassifier.set("sources")
        if (isAndroid) {
            val android = extensions.getByName("android") as BaseExtension
            from(android.sourceSets["main"].java.srcDirs)
        } else {
            val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
            from(sourceSets["main"].allSource)
        }
    }
}

internal fun Project.createDocs(publication: Publication): Jar {
    if (isKotlin) {
        apply<DokkaPlugin>()
        return tasks.create("generatePublisher${publication.name.capitalize()}Docs", Jar::class.java) {
            val task = tasks["dokka"]
            dependsOn(task)
            archiveClassifier.set("javadoc")
            from((task as DokkaTask).outputDirectory)
        }
    } else {
        throw IllegalArgumentException("Release.DOCS_AUTO only works in kotlin projects.")
    }
}

internal val Project.isKotlin get() = plugins.toList().any { it is KotlinBasePluginWrapper }
internal val Project.isAndroid get() = plugins.toList().any { it is AndroidBasePlugin }
internal val Project.isAndroidLibrary get() = plugins.toList().any { it is LibraryPlugin }