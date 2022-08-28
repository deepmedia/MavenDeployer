package io.deepmedia.tools.deployer.impl

import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.kotlin.dsl.newInstance

class DefaultDeploySpec internal constructor(project: Project) : DeploySpec {
    override fun getName() = "_default"
    override val auth: Auth = project.objects.newInstance()
    override val content: Content = project.objects.newInstance()
    override val projectInfo: ProjectInfo = project.objects.newInstance()
    override val release: Release = project.objects.newInstance()
    override val signing: Signing = project.objects.newInstance()
    fun auth(action: Action<Auth>) { action.execute(auth) }
    fun content(action: Action<Content>) { action.execute(content) }
    fun projectInfo(action: Action<ProjectInfo>) { action.execute(projectInfo) }
    fun release(action: Action<Release>) { action.execute(release) }
    fun signing(action: Action<Signing>) { action.execute(signing) }

    init {
        val inferredContent = Content.inferred(project)
        inferredContent.all { content.allComponents.add(this) }
        inferredContent.whenObjectRemoved { content.allComponents.remove(this) }
    }
}