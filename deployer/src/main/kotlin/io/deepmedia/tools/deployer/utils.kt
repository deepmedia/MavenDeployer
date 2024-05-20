@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer

import com.android.build.gradle.LibraryPlugin
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper


internal fun <T> Property<T>.fallback(value: T) {
    convention(value)
}

internal fun <T> Property<T>.fallback(value: Provider<T>) {
    convention(value)
}

private var _hasKotlinPluginClasspath: Boolean? = null

private var _hasAndroidPluginClasspath: Boolean? = null

internal val Project.hasKotlinPluginClasspath: Boolean get() {
    if (_hasKotlinPluginClasspath == null) {
        _hasKotlinPluginClasspath = runCatching {
            Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension")
        }.isSuccess
    }
    return _hasKotlinPluginClasspath!!
}

internal val Project.hasAndroidPluginClasspath: Boolean get() {
    if (_hasAndroidPluginClasspath == null) {
        _hasAndroidPluginClasspath = runCatching {
            Class.forName("com.android.build.gradle.api.AndroidBasePlugin")
        }.isSuccess
    }
    return _hasAndroidPluginClasspath!!
}

internal val Project.isJavaProject get() = plugins.any { it is JavaBasePlugin }

internal val Project.isGradlePluginProject get() = plugins.any { it is JavaGradlePluginPlugin }

internal val Project.isAndroidLibraryProject get() = hasAndroidPluginClasspath && plugins.any { it is LibraryPlugin }

internal val Project.isKmpProject get() = hasKotlinPluginClasspath && plugins.any { it is KotlinMultiplatformPluginWrapper }
internal val Project.isKotlinProject get() = hasKotlinPluginClasspath && plugins.any { it is KotlinBasePluginWrapper }

// internal fun Project.whenJavaPluginApplied(block: () -> Unit) = whenPluginApplied<JavaBasePlugin>(block)
// internal fun Project.whenGradlePluginPluginApplied(block: () -> Unit) = whenPluginApplied<JavaGradlePluginPlugin>(block)
// internal fun Project.whenAndroidLibraryPluginApplied(block: () -> Unit) {
//     if (hasAndroidPluginClasspath) whenPluginApplied<LibraryPlugin>(block)
// }
/* internal fun Project.whenKmpPluginApplied(block: () -> Unit) {
    if (!hasKotlinPluginClasspath) return
    whenPluginApplied<KotlinMultiplatformPluginWrapper>(block)
}
// Not the recommended way but the other approach - use plugin IDs - requires to know the ID and it's not
// clear if withId() is invoked when the plugin is applied manually or only in the plugin block
internal inline fun <reified P: Plugin<*>> Project.whenPluginApplied(crossinline block: () -> Unit) {
    plugins.withType<P>().all { block() }
} */

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