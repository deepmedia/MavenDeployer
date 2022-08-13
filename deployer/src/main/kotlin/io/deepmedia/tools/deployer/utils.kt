@file:Suppress("ObjectPropertyName")

package io.deepmedia.tools.deployer

import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap


internal fun <T> Property<T>.fallback(value: T) {
    convention(value)
}

internal fun <T> Property<T>.fallback(value: Provider<T>) {
    convention(value)
}

private var _hasKotlinPluginClasspath: Boolean? = null

private var _hasAndroidPluginClasspath: Boolean? = null

internal val Project.hasKotlinPluginClasspath: Boolean get() {
    if (_hasKotlinPluginClasspath == null) {
        _hasKotlinPluginClasspath = runCatching {
            Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension")
        }.isSuccess
    }
    return _hasKotlinPluginClasspath!!
}

internal val Project.hasAndroidPluginClasspath: Boolean get() {
    if (_hasAndroidPluginClasspath == null) {
        _hasAndroidPluginClasspath = runCatching {
            Class.forName("com.android.build.gradle.api.AndroidBasePlugin")
        }.isSuccess
    }
    return _hasAndroidPluginClasspath!!
}

internal val Project.isJavaProject get() = plugins.any { it is JavaBasePlugin }

internal val Project.isGradlePluginProject get() = plugins.any { it is JavaGradlePluginPlugin }

internal val Project.isAndroidLibraryProject get() = hasAndroidPluginClasspath && plugins.any { it is LibraryPlugin }

internal val Project.isKotlinProject get() = hasKotlinPluginClasspath && plugins.any { it is KotlinBasePluginWrapper }

internal val Project.isKotlinMultiplatformProject get() = hasKotlinPluginClasspath && plugins.any { it is KotlinMultiplatformPluginWrapper }

internal val MavenArtifact.isSourcesJar get() = classifier == "sources" && extension == "jar"

internal val MavenArtifact.isJavadocJar get() = classifier == "javadoc" && extension == "jar"
