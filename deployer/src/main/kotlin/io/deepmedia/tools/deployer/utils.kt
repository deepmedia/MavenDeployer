@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper

internal fun String.capitalized(): String {
    return replaceFirstChar { it.uppercaseChar() }
}

internal val Project.isKotlinProject get() = plugins.any { it is KotlinBasePluginWrapper }

internal fun MavenArtifactSet.dump(): String {
    return if (isEmpty()) "[]"
    else joinToString(separator = "\n\t", prefix = "\n\t") { it.dump() }
}

internal fun MavenArtifact.dump(): String {
    return "${file.name} - $extension - $classifier (${file.absolutePath})"
}

internal fun SoftwareComponent.dump(): String {
    val variants = runCatching { (this as SoftwareComponentInternal).usages }.getOrElse {
        return "Can't dump: $it"
    }
    return variants.joinToString(separator = "\n\t", prefix = "\n\t") {
        val artifacts = runCatching {
            it.artifacts.map { "${it.name} (${it.file}, ${it.type}, ${it.extension}, ${it.classifier})" }
        }.getOrElse { listOf("Could not dump: $it") }
        "${it.name}: ${artifacts.joinToString(separator = "\n\t\t", prefix = "\n\t\t")}"
    }
}

internal inline fun <reified T: Task> TaskContainer.maybeRegister(name: String, crossinline configure: T.() -> Unit): TaskProvider<T> {
    return runCatching { named<T>(name) }.getOrElse {
        register<T>(name) { configure() }
    }
}

// Many projects use state.executed as a signal, but that's not exactly the condition
// that makes afterEvaluate throws. And we don't want to drop afterEvaluate when possible,
// because calling afterEvaluate inside an afterEvaluate, jumps to the next loop which is good
// to execute stuff after other plugins.
private fun Project.canCallAfterEvaluate(): Boolean {
    val state = state
    return if (state is ProjectStateInternal) {
        state.isUnconfigured || state.isConfiguring
    } else {
        !state.executed // Less precise
    }
}

internal fun Project.whenEvaluated(block: () -> Unit) {
    if (canCallAfterEvaluate()) afterEvaluate { block() }
    else block()
}