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

private val localPropertiesCache = ConcurrentHashMap<Project, Properties>()

internal val MavenArtifact.isSourcesJar get() = classifier == "sources" && extension == "jar"

internal val MavenArtifact.isJavadocJar get() = classifier == "javadoc" && extension == "jar"

/* internal fun missingField(field: String): Nothing {
    error("Could not determine '$field'. Please specify it explicitly.")
} */

internal fun Project.getSecretOrThrow(key: String, field: String): String {
    return getSecretOrNull(key) ?: error("Secret key $field not found in environment variables nor properties.")
}

internal fun Project.getSecretOrNull(key: String): String? {
    // Try with environmental variable.
    val env: String? = System.getenv(key)
    if (!env.isNullOrEmpty()) return env
    // Try with findProperty.
    val project = findProperty(key) as? String
    if (!project.isNullOrEmpty()) return project
    // Try with local.properties file.
    val properties = localPropertiesCache.getOrPut(rootProject) {
        val properties = Properties()
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            val stream = FileInputStream(file)
            properties.load(stream)
        }
        properties
    }
    val local = properties!!.getProperty(key)
    if (!local.isNullOrEmpty()) return local
    // We failed. Return null.
    return null
}