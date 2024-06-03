package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project

internal interface Inference {
    fun inferComponents(
        project: Project,
        spec: DeploySpec,
        create: (Component.() -> Unit) -> Component
    )
}