package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.isKotlinProject
import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Android targets can be configured for publishing in two different ways:
 * 1. via AGP publishing block
 *   In this case, users use AGP APIs to explicitly mark some variants to be publishable, or a multi-variant component
 *   with a custom name. The name of the single variant, or the custom name of a multi-variant publiation, will be
 *   the name of the software component.
 *   https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/build-system/gradle-api/src/main/java/com/android/build/api/dsl/LibraryPublishing.kt
 *   NOTE: there doesn't seem to be any way of reading variants from AGP
 *
 * 2. Via KGP's configuration block.
 *    In this case, the software components can be retrieved using [KotlinTarget.components] as we do in fromKotlinTarget.
 *    But there are a few things to consider:
 *    - there may be no components at all, if user did not configure any variant
 *    - there may be more than one component, if user chose to do so
 *    - [KotlinTarget.components] *must* be called in a afterEvaluate block, after AGP's afterEvaluate.
 *
 */
internal class AndroidInference(private val componentNames: List<String>) : Inference {

    override fun inferComponents(project: Project, spec: DeploySpec, create: (Component.() -> Unit) -> Component) {
        project.plugins.withId("com.android.library") {
            val kotlin = if (project.isKotlinProject) project.kotlinExtension else null
            componentNames.forEach { componentName ->
                create {
                    fromSoftwareComponent(componentName)

                    packaging.set("aar")

                    if (kotlin is KotlinMultiplatformExtension) {
                        val androidTargets = kotlin.targets.matching { it.platformType == KotlinPlatformType.androidJvm }
                        artifactId.set {
                            val target = androidTargets.firstOrNull { it.components.any { it.name == componentName } } ?: androidTargets.first()
                            "$it-${target.name.lowercase()}"
                        }
                    }
                }
            }
        }
    }
}