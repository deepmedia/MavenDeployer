package io.deepmedia.tools.deployer.model

import org.gradle.api.Project
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Secret(val key: String) {
    internal fun resolve(project: Project, location: String): String {
        return project.findSecret(key)
            ?: error("Secret key $key (from $location) not found in environment variables nor properties.")
    }
}

private val localPropertiesCache = ConcurrentHashMap<Project, Properties>()

private fun Project.findSecret(key: String): String? {
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

interface SecretScope {
    fun secret(key: String) = Secret(key)
}