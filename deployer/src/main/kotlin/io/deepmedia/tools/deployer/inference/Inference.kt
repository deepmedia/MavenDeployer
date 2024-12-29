package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project


internal typealias InferenceComponentFactory = (runUserAction: Boolean, configure: Component.() -> Unit) -> Component

internal interface Inference {
    fun inferComponents(
        project: Project,
        spec: DeploySpec,
        create: InferenceComponentFactory
    )
}