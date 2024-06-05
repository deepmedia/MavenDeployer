@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.capitalized
import io.deepmedia.tools.deployer.maybeRegister
import io.deepmedia.tools.deployer.model.Artifacts
import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.File

internal val MavenArtifact.isDocsJar get() = classifier == "javadoc" && extension == "jar"

/**
 * These jars (just like any other artifacts) may be signed if the spec is configured to do so.
 * When a signed artifact is shared between different specs, Gradle complains because it detects
 * an unregistered dependency between the publish* task of one of the spec, and the sign* task of the other.
 *
 * For this reason, we copy jars into separate folders.
 */
internal fun Project.makeDocsJar(
    spec: DeploySpec,
    component: Component,
    task: TaskProvider<Jar>
): Artifacts.Entry {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    val publication = component.maybeCreatePublication(publications, spec).name
    val targetDir = layout.buildDirectory.dir("deploy").get().dir("docs").dir(publication)
    val targetName = "${spec.projectInfo.resolvedArtifactId(component).get()}-${spec.release.resolvedVersion.get()}-javadoc.jar"
    return Artifacts.Entry(
        artifact = File(targetDir.asFile, targetName),
        extension = "jar",
        classifier = "javadoc",
        builtBy = tasks.maybeRegister<Copy>("makeDocsJarFor${publication.capitalized()}") {
            from(task)
            into(targetDir)
            rename { it.replace(task.get().archiveFileName.get(), targetName) }
        }
    )
}

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