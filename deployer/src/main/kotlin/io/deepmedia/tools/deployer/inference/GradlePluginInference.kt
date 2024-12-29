package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.whenEvaluated
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension

/**
 * When java-gradle-plugin is applied and isAutomatedPublishing is true, X+1 publications are created.
 *
 * First is "pluginMaven" with the artifact, and the rest are plugin markers
 * called "<PLUGIN_NAME>PluginMarkerMaven". Markers have no jar, just pom file with single dep.
 *
 * See [Component.fromGradlePluginDeclaration] for marker components.
 */
internal class GradlePluginInference : Inference {

    override fun inferComponents(project: Project, spec: DeploySpec, create: InferenceComponentFactory) {
        project.plugins.withId("java-gradle-plugin") {
            val gradlePlugin = project.extensions.getByType<GradlePluginDevelopmentExtension>()
            project.whenEvaluated {
                if (!gradlePlugin.isAutomatedPublishing) return@whenEvaluated
                inferComponents(project, spec, gradlePlugin, create)
            }
        }
    }

    private fun inferComponents(project: Project, spec: DeploySpec, gradlePlugins: GradlePluginDevelopmentExtension, create: InferenceComponentFactory) {
        val mainComponent = create(true) {
            fromMavenPublication("pluginMaven", clone = true)
            packaging.set("jar")
        }
        gradlePlugins.plugins.all {
            create(false) {
                fromGradlePluginDeclaration(this@all, mainComponent, clone = true)
            }
        }
    }
}