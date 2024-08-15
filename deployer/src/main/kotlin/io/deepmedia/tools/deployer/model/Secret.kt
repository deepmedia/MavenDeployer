package io.deepmedia.tools.deployer.model

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Secret(val key: String) {
    internal val hasKey get() = key.isNotEmpty()

    internal fun resolveOrThrow(providers: ProviderFactory, layout: ProjectLayout, location: String): String {
        return resolveOrNull(providers, layout) ?: error("Secret key $key (from $location) not found in environment variables nor properties.")
    }
    internal fun resolveOrNull(providers: ProviderFactory, layout: ProjectLayout): String? {
        if (!hasKey) return null
        return findSecret(key, providers, layout)
    }
}

private val localPropertiesCache = ConcurrentHashMap<File, Properties>()

private fun findSecret(key: String, providers: ProviderFactory, layout: ProjectLayout): String? {
    // Try with environmental variable.
    val env = providers.environmentVariable(key).orNull
    if (!env.isNullOrEmpty()) return env

    // Try with findProperty.
    val prop = providers.gradleProperty(key).orNull
    if (!prop.isNullOrEmpty()) return prop

    // Try with local.properties file.
    val dir = layout.projectDirectory.asFile
    val local = localPropertiesCache.getOrPut(dir) { dir.localProperties() ?: Properties() }?.getProperty(key)
    if (!local.isNullOrEmpty()) return local

    // We failed. Return null.
    return null
}

private fun File.localProperties(): Properties? {
    val child = File(this, "local.properties")
    if (child.exists()) {
        val properties = Properties()
        child.inputStream().use { properties.load(it) }
        return properties
    }
    return parentFile?.localProperties()
}

interface SecretScope {
    fun secret(key: String) = Secret(key)
    fun absent() = Secret("")
}