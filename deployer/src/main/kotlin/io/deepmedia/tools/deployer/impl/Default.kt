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


    /* init {
        val inferredComponents = inferredComponents(project)
        inferredComponents.all { content.allComponents.add(this) }
        inferredComponents.whenObjectRemoved { content.allComponents.remove(this) }
    } */
}