package io.deepmedia.tools.deployer.specs

import io.deepmedia.tools.deployer.model.AbstractDeploySpec
import io.deepmedia.tools.deployer.model.Auth
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.maven
import kotlin.math.abs

class LocalDeploySpec internal constructor(objects: ObjectFactory, name: String)
    : AbstractDeploySpec<Auth>(objects, name, Auth::class) {
    val directory: DirectoryProperty = objects.directoryProperty().apply { finalizeValueOnRead() }

    override fun registerRepository(target: Project, repositories: RepositoryHandler): MavenArtifactRepository {
        return if (directory.isPresent) {
            val hash = abs(directory.get().asFile.absolutePath.hashCode()).toString()
            repositories.maven(directory) { this.name = hash }
        } else {
            repositories.mavenLocal()
        }
    }

    override fun fallback(to: DeploySpec) {
        super.fallback(to)
        if (to is LocalDeploySpec) {
            directory.convention(to.directory)
        }
    }
}