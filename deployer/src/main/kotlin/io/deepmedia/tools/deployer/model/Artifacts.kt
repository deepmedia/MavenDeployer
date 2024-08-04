package io.deepmedia.tools.deployer.model

import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.domainObjectSet
import javax.inject.Inject

open class Artifacts @Inject constructor(objects: ObjectFactory) {

    internal sealed class Entry {
        sealed class Resolved(private val builtByCandidate: Any?) : Entry() {
            abstract val artifact: Any

            // Unwraps dictionary notation and providers, so the result should be one of the accepted types
            // (PublishArtifact, AbstractArchiveTask, or something resolvable with file())
            val unwrappedArtifact: Any get() {
                val unwrapDictionary = when (this) {
                    is Dictionary -> this.source
                    is Regular -> this.artifact
                }
                return when (unwrapDictionary) {
                    is Provider<*> -> unwrapDictionary.get()
                    else -> unwrapDictionary
                }
            }

            // Note that MavenPublication.artifact() *should* already correctly pull the dependencies,
            // but we use an intermediate task (Workarounds.kt) and it is important to keep them explicitly
            val builtBy: Any? get() = builtByCandidate ?: when (val raw = unwrappedArtifact) {
                is PublishArtifact -> raw.buildDependencies
                is AbstractArchiveTask -> raw
                else -> null
            }
        }

        class Regular(override val artifact: Any, builtBy: Any?) : Resolved(builtBy)

        class Dictionary(private val dictionary: Map<String, Any?>, builtBy: Any?) : Resolved(builtBy) {
            constructor(source: Any, classifier: String?, extension: String, builtBy: Any?) : this(
                mapOf(
                    "source" to source,
                    "classifier" to classifier,
                    "extension" to extension
                ),
                builtBy
            )
            val source: Any get() = dictionary["source"]!!
            val classifier: String? get() = dictionary["classifier"] as? String
            val extension: String get() = dictionary["extension"] as String
            override val artifact get() = dictionary
        }

        class Promise(val make: (Project) -> Any) : Entry() {
            fun resolve(project: Project): Resolved = Unknown(make(project), null)
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun Unknown(artifact: Any, builtBy: Any?): Resolved = when {
                artifact is Map<*, *> -> Dictionary(artifact as Map<String, Any?>, builtBy)
                else -> Regular(artifact, builtBy)
            }
        }
    }

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
        entries.add(Entry.Unknown(artifact, builtBy))
    }

    fun artifact(artifact: Any, classifier: String?, extension: String, builtBy: Any? = null) {
        entries.add(Entry.Dictionary(artifact, classifier, extension, builtBy))
    }
}