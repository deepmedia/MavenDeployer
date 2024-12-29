@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.maybeRegister
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension


internal val MavenArtifact.isSourcesJar get() = classifier == "sources" && extension == "jar"

internal val Project.makeEmptySourcesJar get() = tasks.maybeRegister<Jar>("makeEmptySourcesJar") {
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("deployer").get().dir("builtins").dir("emptySources"))
}

internal val Project.makeKotlinSourcesJar: TaskProvider<Jar> get() = tasks.maybeRegister("makeKotlinSourcesJar") {
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("deployer").get().dir("builtins").dir("kotlinSources"))

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
    destinationDirectory.set(layout.buildDirectory.dir("deployer").get().dir("builtins").dir("javaSources"))

    val java = project.extensions.getByType<JavaPluginExtension>()
    from(java.sourceSets["main"].allSource)
}