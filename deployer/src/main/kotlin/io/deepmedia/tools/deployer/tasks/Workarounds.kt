package io.deepmedia.tools.deployer.tasks

import io.deepmedia.tools.deployer.Logger
import io.deepmedia.tools.deployer.capitalized
import io.deepmedia.tools.deployer.model.Artifacts
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.configureSigning
import io.deepmedia.tools.deployer.maybeRegister
import io.deepmedia.tools.deployer.model.Component
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File

/**
 * (Note: even if the signing plugin gets fixed, we may still need this task to give the file the correct name)
 *
 * This workaround is needed due to the combination of the following facts:
 *
 * - Any artifact that becomes part of a publication may end up being signed during [configureSigning].
 *
 * - Signing task will sign files in place, meaning, foo/hello.txt gets foo/hello.txt.* signed siblings.
 *   This is not configurable.
 *
 * - Such artifacts may end up being part of multiple publications, each with its own signing task.
 *   For example, our own emptyDocs() artifact-generating task may be part of multiple components or specs.
 *   The same would happen for a user-generated task (say dokkaJavadocJar) as long as there are more than 1 spec.
 *
 * What this means as a whole is that, when each signing task receive its publication, it configures itself
 * to use the file as an input and the signed files as output. These tasks will share the same inputs and outputs.
 * Gradle is not smart enough to detect this and can spot an unregistered dependency between:
 * - the sign* task of one of the spec (signing foo/hello.txt)
 * - the publish* task of some other spec (using foo/hello.txt and its signed files)
 *
 * Unfortunately Gradle does not detect that there's already some other signing task that satisfies the missing
 * dependency. To fix this:
 *
 * 1. One option would be configure each signing task to output in its own directory. This is not possible (no API).
 *    One may copy the signed files somewhere else, but then one would also have to manually wire Sign and MavenPublication,
 *    something that's done by default when using SignExtension.sign(MavenPublication). We wouldn't be able to use it anymore.
 *
 * 2. Second option is to wrap all artifact-providing tasks in a custom task that moves them elsewhere.
 *    This way, Sign will sign *different files* for each spec and there's no possibility of a clash.
 *
 * We go the second route below, which is a bit tricky because we have to deal with [Any] types.
 *
 * [Artifacts.Entry.Resolved.artifactForMaven]: May be one of
 * - [org.gradle.api.artifacts.PublishArtifact]. Extension and classifier values are taken from the wrapped instance.
 * - [org.gradle.api.tasks.bundling.AbstractArchiveTask]. Extension and classifier values are taken from the wrapped instance.
 * - Anything that can be resolved to a [java.io.File] via the [org.gradle.api.Project.file] method.
 * - A [java.util.Map] that contains a 'source' entry that can be resolved as any of the other input types, including file.
 *   This map can contain a 'classifier' and an 'extension' entry to further configure the constructed artifact.
 *
 * [Artifacts.Entry.Resolved.builtBy] follows the rules of [org.gradle.api.Task.dependsOn], so it's not a big problem,
 * because we want to pass this exactly to the dependsOn API.
 */
internal fun Artifacts.Entry.Resolved.wrapped(project: Project, logger: Logger, spec: DeploySpec, component: Component, artifactId: String): Artifacts.Entry.Resolved {
    logger { "[Workarounds] applying to ${spec.name} / ${component.shortName} / $artifactId..." }

    // This can create issues. There are times at which it's not possible to create a new publication
    // AbstractMutationGuard$IllegalMutationException: NamedDomainObjectContainer#create(String, Class) on Publication container cannot be executed in the current context.
    /* val (resolvedFile, resolvedClassifier, resolvedExtension) = run {
        val extension = project.extensions.getByType<PublishingExtension>()
        val internalPublication = extension.publications.maybeCreate("internalMavenDeployer", MavenPublication::class.java)
        val resolved = internalPublication.artifact(artifactForMaven)
        internalPublication.setArtifacts(emptyList<Any?>())
        listOf(resolved.file, resolved.classifier, resolved.extension)
    } */

    val resolvedFile = when (val data = unwrappedArtifact) {
        is PublishArtifact -> data.file
        is AbstractArchiveTask -> data.archiveFile.get().asFile
        else -> project.file(data)
    }

    val (resolvedClassifier, resolvedExtension) = when (this) {
        is Artifacts.Entry.Dictionary -> this.classifier to this.extension
        else -> when (val data = unwrappedArtifact) {
            is PublishArtifact -> data.classifier to data.extension
            is AbstractArchiveTask -> data.archiveClassifier.orNull to data.archiveExtension.get()
            else -> resolvedFile.nameWithoutExtension.split("-").last() to resolvedFile.extension
        }
    }

    val targetDir = project.layout.buildDirectory.dir("deployer").get()
        .dir(spec.name)
        .dir(component.shortName + " - " + artifactId)

    val targetName = buildString {
        append(spec.projectInfo.resolvedArtifactId(component).get())
        append("-")
        append(spec.release.resolvedVersion.get())
        if (resolvedClassifier != null) {
            append("-")
            append(resolvedClassifier)
        }
        append(".")
        append(resolvedExtension)
    }

    val copyTaskName = "copy${spec.name.capitalized()}${component.shortName.capitalized()}${artifactId.capitalized()}"
    val copyTask = project.tasks.maybeRegister<Copy>(copyTaskName) {
        builtBy?.let { dependsOn(it) }
        from(resolvedFile)
        into(targetDir)
        rename { it.replace(resolvedFile.name, targetName) }
    }
    return Artifacts.Entry.Dictionary(File(targetDir.asFile, targetName), resolvedClassifier, resolvedExtension, copyTask).also {
        logger { "[Workarounds] artifact mapping (${resolvedClassifier}): ${resolvedFile.path} => $targetName in ${targetDir.asFile.path}" }
    }
}