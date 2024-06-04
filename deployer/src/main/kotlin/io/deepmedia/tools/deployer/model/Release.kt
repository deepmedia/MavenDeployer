package io.deepmedia.tools.deployer.model

import com.android.build.gradle.BaseExtension
import io.deepmedia.tools.deployer.isAndroidLibraryProject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class Release @Inject constructor(objects: ObjectFactory) {

    val version: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }
    val tag: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }
    val description: Property<String> = objects.property<String>().apply { finalizeValueOnRead() }

    internal lateinit var resolvedVersion: Provider<String>
    internal lateinit var resolvedTag: Provider<String>
    internal lateinit var resolvedDescription: Provider<String>
    // internal lateinit var resolvedPackaging: Provider<String>

    internal fun fallback(to: Release) {
        version.convention(to.version)
        tag.convention(to.tag)
        description.convention(to.description)
        // packaging.fallback(to.packaging)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun resolve(target: org.gradle.api.Project, spec: DeploySpec) {
        // Version defaults to target.version.
        // Tag defaults to v$version.
        // Description defaults to target name + tag
        // Packaging can be set to aar for AARs.
        resolvedVersion = version.orElse(target.provider {
            when {
                target.isAndroidLibraryProject -> {
                    val android = target.extensions.getByName("android") as BaseExtension
                    android.defaultConfig.versionName + (android.defaultConfig.versionNameSuffix ?: "")
                }
                else -> target.version.toString()
            }
        })

        resolvedTag = tag.orElse(resolvedVersion.map { "v$it" })
        resolvedDescription = description.orElse(tag.map { "${target.name} $it" })
    }
}