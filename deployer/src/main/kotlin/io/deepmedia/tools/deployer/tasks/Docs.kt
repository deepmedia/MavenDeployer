@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.maybeRegister
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.bundling.Jar

internal val MavenArtifact.isDocsJar get() = classifier == "javadoc" && extension == "jar"

internal val Project.makeEmptyDocsJar get() = tasks.maybeRegister<Jar>("makeEmptyDocsJar") {
    archiveClassifier.set("javadoc")
}

/* private val Project.makeAutoDocsJar: TaskProvider<Jar> get() {
    // if (component.isMarker.get()) return null // markers shouldn't have any jars
    if (!isKotlinProject) return makeEmptyDocsJar
    plugins.apply(DokkaPlugin::class)
    val dokkaHtml = tasks.named("dokkaHtml", DokkaTask::class.java)
    return tasks.maybeRegister<Jar>("makeAutoDocsJar") {
        dependsOn(dokkaHtml)
        from(dokkaHtml.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }
} */