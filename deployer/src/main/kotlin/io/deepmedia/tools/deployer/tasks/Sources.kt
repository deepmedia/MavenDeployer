@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import com.android.build.api.dsl.LibraryExtension
import io.deepmedia.tools.deployer.*
import io.deepmedia.tools.deployer.isKmpProject
import io.deepmedia.tools.deployer.isKotlinProject
import io.deepmedia.tools.deployer.maybeRegister
import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Artifacts
import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
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
internal fun Project.makeSourcesJar(spec: DeploySpec, component: Component, empty: Boolean): Artifacts.Entry? {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    val publication = component.maybeCreatePublication(publications, spec).name
    val sourceTask = if (empty) makeEmptySourcesJar else makeAutoSourcesJar
    val targetDir = layout.buildDirectory.dir("deploy").get().dir("sources").dir(publication)
    val targetName = "${spec.projectInfo.resolvedArtifactId(component).get()}-${spec.release.resolvedVersion.get()}-sources.jar"
    return if (sourceTask == null) null else Artifacts.Entry(
        artifact = File(targetDir.asFile, targetName),
        extension = "jar",
        classifier = "sources",
        builtBy = tasks.maybeRegister<Copy>("makeSourcesJarFor${publication.capitalize()}") {
            from(sourceTask)
            into(targetDir)
            rename { it.replace(sourceTask.get().archiveFileName.get(), targetName) }
        }
    )
}

private val Project.makeEmptySourcesJar get() = tasks.maybeRegister<Jar>("makeEmptySourcesJar") {
    archiveClassifier.set("sources")
}

private val Project.makeAutoSourcesJar: TaskProvider<Jar>? get() {
    return when {
        // Markers shouldn't have any docs
        // component.isMarker.get() -> null
        // Too hard to do, also not needed because KMP has sources.
        isKmpProject -> null
        // Android plugin extension now has option to enable sources, withSourcesJar().
        // It's not clear if we should trigger that here or do our own implementation,
        // we don't know what component this will be called on (if the inferred one or custom...)
        // and what the Android build type is. Also withSourcesJar() is kind of recent...
        isAndroidLibraryProject -> null
        // As of 1.7.10, for some reason Kotlin projects do not have sources unlike KMP.
        isKotlinProject -> tasks.maybeRegister<Jar>("makeAutoSourcesJar") {
            archiveClassifier.set("sources")
            val kotlin = project.kotlinExtension
            kotlin.sourceSets.forEach {
                from(it.kotlin) {
                    // kotlin plugin does this
                    into(it.name)
                    duplicatesStrategy = DuplicatesStrategy.WARN
                }
            }

        }
        isJavaProject -> tasks.maybeRegister<Jar>("makeAutoSourcesJar") {
            archiveClassifier.set("sources")
            val java = project.extensions.getByType<JavaPluginExtension>()
            from(java.sourceSets["main"].allSource)
        }
        else -> null
    }
}
