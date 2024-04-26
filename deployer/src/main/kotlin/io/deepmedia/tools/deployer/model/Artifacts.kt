package io.deepmedia.tools.deployer.model

import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.domainObjectSet
import javax.inject.Inject

open class Artifacts @Inject constructor(objects: ObjectFactory) {

    internal class Entry(val artifact: Any, val builtBy: Any?)

    internal val entries = objects.domainObjectSet(Entry::class)

    /**
     * [artifact] follows the rules of [MavenPublication.artifact] function. This means that it can be:
     *
     * - A [org.gradle.api.artifacts.PublishArtifact]. Extension and classifier values are taken from the wrapped instance.
     * - A [org.gradle.api.tasks.bundling.AbstractArchiveTask]. Extension and classifier values are taken from the wrapped instance.
     * - Anything that can be resolved to a [java.io.File] via the [org.gradle.api.Project.file] method.
     *     Extension and classifier values are interpolated from the file name.
     * - A [java.util.Map] that contains a 'source' entry that can be resolved as any of the other input types, including file.
     *     This map can contain a 'classifier' and an 'extension' entry to further configure the constructed artifact.
     *
     * [builtBy] follows the rules of [org.gradle.api.Task.dependsOn]. This means that it can be:
     *
     * - A [String] task path or name. These task references will not cause task creation.
     * - A [org.gradle.api.Task].
     * - A [org.gradle.api.tasks.TaskDependency].
     * - A [org.gradle.api.tasks.TaskReference].
     * - A [org.gradle.api.Buildable].
     * - A [org.gradle.api.file.RegularFileProperty] or [org.gradle.api.file.DirectoryProperty].
     * - A provider of any of the types in this list.
     * - An iterable, collection, map or array of any of the types in this list.
     * - A Callable. The call() method may return any of the types in this list.
     * - A Kotlin function or Groovy closure. It may return any of the types in this list.
     */
    fun artifact(artifact: Any, builtBy: Any? = null) {
        entries.add(Entry(artifact, builtBy))
    }

    fun artifact(artifact: Any, classifier: String, extension: String, builtBy: Any? = null) {
        artifact(
            artifact = mapOf(
                "source" to artifact,
                "classifier" to classifier,
                "extension" to extension
            ),
            builtBy = builtBy
        )
    }
}