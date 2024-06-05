@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.capitalized
import io.deepmedia.tools.deployer.maybeRegister
import io.deepmedia.tools.deployer.model.Artifacts
import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import java.io.File


internal val MavenArtifact.isSourcesJar get() = classifier == "sources" && extension == "jar"

/**
 * These jars (just like any other artifacts) may be signed if the spec is configured to do so.
 * When a signed artifact is shared between different specs, Gradle complains because it detects
 * an unregistered dependency between the publish* task of one of the spec, and the sign* task of the other.
 *
 * For this reason, we copy jars into separate folders.
 */
internal fun Project.makeSourcesJar(
    spec: DeploySpec,
    component: Component,
    task: TaskProvider<Jar>
): Artifacts.Entry {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    val publication = component.maybeCreatePublication(publications, spec).name
    val targetDir = layout.buildDirectory.dir("deploy").get().dir("sources").dir(publication)
    val targetName = "${spec.projectInfo.resolvedArtifactId(component).get()}-${spec.release.resolvedVersion.get()}-sources.jar"
    return Artifacts.Entry(
        artifact = File(targetDir.asFile, targetName),
        extension = "jar",
        classifier = "sources",
        builtBy = tasks.maybeRegister<Copy>("makeSourcesJarFor${publication.capitalized()}") {
            from(task)
            into(targetDir)
            rename { it.replace(task.get().archiveFileName.get(), targetName) }
        }
    )
}

internal val Project.makeEmptySourcesJar get() = tasks.maybeRegister<Jar>("makeEmptySourcesJar") {
    archiveClassifier.set("sources")
}

internal val Project.makeKotlinSourcesJar: TaskProvider<Jar> get() = tasks.maybeRegister("makeKotlinSourcesJar") {
    archiveClassifier.set("sources")
    val kotlin = project.kotlinExtension
    kotlin.sourceSets.all {
        val sourceSet = this
        from(sourceSet.kotlin) {
            into(sourceSet.name) // kotlin plugin does this
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
    }
}

internal val Project.makeJavaSourcesJar: TaskProvider<Jar> get() = tasks.maybeRegister("makeJavaSourcesJar") {
    archiveClassifier.set("sources")
    val java = project.extensions.getByType<JavaPluginExtension>()
    from(java.sourceSets["main"].allSource)
}