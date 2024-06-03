package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import io.deepmedia.tools.deployer.whenEvaluated
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget

internal class KotlinInference : Inference {
    private val pluginIds = listOf(
        "org.jetbrains.kotlin.jvm",
        "org.jetbrains.kotlin.js",
        "org.jetbrains.kotlin.android",
        "org.jetbrains.kotlin.multiplatform"
    )

    private fun inferComponent(target: KotlinTarget, multiplatform: Boolean, create: (Component.() -> Unit) -> Component) {
        if (target is KotlinOnlyTarget<*>) {
            create {
                fromKotlinTarget(target)
                if (multiplatform && target.platformType != KotlinPlatformType.common) {
                    artifactId.set { "$it-${target.name.lowercase()}" }
                }
            }
        } else if (target is KotlinAndroidTarget) {
            // Android's KotlinTarget.components must be called in afterEvaluate,
            // possibly nested so we jump after AGP's afterEvaluate blocks.
            // There may be 0, 1 or more components depending on how KotlinAndroidTarget was configured.
            // NOTE: if multiple components are present, they will have the same artifactId.
            target.project.whenEvaluated {
                target.project.whenEvaluated {
                    (target as KotlinTarget).components.forEach { component ->
                        create {
                            fromSoftwareComponent(component, tag = target)
                            if (multiplatform) {
                                artifactId.set { "$it-${target.name.lowercase()}" }
                            }
                        }
                    }
                }
            }
        } else {
            error("Unexpected kotlin target: $target")
        }
    }

    override fun inferComponents(project: Project, spec: DeploySpec, create: (Component.() -> Unit) -> Component) {
        pluginIds.forEach { pluginId ->
            project.plugins.withId(pluginId) {
                val kotlin = project.kotlinExtension
                if (kotlin is KotlinMultiplatformExtension) {
                    // Kotlin multiplatform projects have one artifact per target, plus one for metadata
                    // which is also exposed as a target with common platform.
                    kotlin.targets.all { inferComponent(this, true, create) }
                } else if (kotlin is KotlinSingleTargetExtension<*>) {
                    kotlin.target.let { inferComponent(it, false, create) }
                } else {
                    error("Unexpected kotlin extension: $kotlin")
                }
            }
        }
    }
}