package io.deepmedia.tools.deployer.specs

import io.deepmedia.tools.deployer.model.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance

class DefaultDeploySpec internal constructor(objects: ObjectFactory) : DeploySpec {
    override fun getName() = "_default"
    override val auth: Auth = objects.newInstance()
    override val content: Content = objects.newInstance()
    override val projectInfo: ProjectInfo = objects.newInstance()
    override val release: Release = objects.newInstance()
    override val signing: Signing = objects.newInstance()


    /* init {
        val inferredComponents = inferredComponents(project)
        inferredComponents.all { content.allComponents.add(this) }
        inferredComponents.whenObjectRemoved { content.allComponents.remove(this) }
    } */
}