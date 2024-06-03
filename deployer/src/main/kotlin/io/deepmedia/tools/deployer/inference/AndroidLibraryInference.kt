package io.deepmedia.tools.deployer.inference

import io.deepmedia.tools.deployer.model.Component
import io.deepmedia.tools.deployer.model.DeploySpec
import org.gradle.api.Project

/**
 * Android targets can be configured for publishing in two different ways:
 * 1. via AGP publishing block
 *   In this case, users use AGP APIs to explicitly mark some variants to be publishable, or a multi-variant component
 *   with a custom name. The name of the single variant, or the custom name of a multi-variant publiation, will be
 *   the name of the software component.
 *   https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/build-system/gradle-api/src/main/java/com/android/build/api/dsl/LibraryPublishing.kt
 *   TODO: there doesn't seem to be any way of reading variants from AGP
 *
 * 2. Via KGP's configuration block.
 *    In this case, the software components can be retrieved using [KotlinTarget.components] as we do in fromKotlinTarget.
 *    But there are a few things to consider:
 *    - there may be no components at all, if user did not configure any variant
 *    - there may be more than one component, if user chose to do so
 *    - [KotlinTarget.components] *must* be called in a afterEvaluate block, after AGP's afterEvaluate.
 *
 * We can't support 1. until a proper AGP API exist.
 * We do already support 2. through KotlinInference, so this is useless for now.
 */
internal class AndroidLibraryInference : Inference {
    override fun inferComponents(project: Project, spec: DeploySpec, create: (Component.() -> Unit) -> Component) {
        TODO("Not yet implemented")
    }
}