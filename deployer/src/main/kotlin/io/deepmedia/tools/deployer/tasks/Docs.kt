@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.isKmpProject
import io.deepmedia.tools.deployer.isKotlinProject
import io.deepmedia.tools.deployer.maybeRegister
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask

internal val MavenArtifact.isDocsJar get() = classifier == "javadoc" && extension == "jar"

internal val Project.makeEmptyDocsJar get() = tasks.maybeRegister<Jar>("makeEmptyDocsJar") {
    archiveClassifier.set("javadoc")
}

internal val Project.makeAutoDocsJar get(): TaskProvider<Jar> {
    if (!isKotlinProject) return makeEmptyDocsJar
    plugins.apply(DokkaPlugin::class)
    val dokkaHtml = tasks.named("dokkaHtml", DokkaTask::class.java)
    return tasks.maybeRegister<Jar>("makeAutoDocsJar") {
        dependsOn(dokkaHtml)
        from(dokkaHtml.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }
}