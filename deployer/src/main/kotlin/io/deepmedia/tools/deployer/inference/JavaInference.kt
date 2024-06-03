package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project

/**
 * Unused, replaced with Component.fromJava() instead.
 */
internal class JavaInference : Inference {
    override fun inferComponents(project: Project, spec: DeploySpec, create: (Component.() -> Unit) -> Component) {
        project.plugins.withId("java") {
            create {
                fromSoftwareComponent("java")
            }
        }
    }
}