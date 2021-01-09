package io.deepmedia.tools.publisher

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.maven.MavenPublication
import java.io.FileInputStream
import java.util.*

internal abstract class PublicationHandler(protected val target: Project) {

    abstract fun ownsPublication(name: String): Boolean

    abstract fun createPublication(name: String): Publication

    abstract fun fillPublication(publication: Publication)

    abstract fun checkPublication(publication: Publication)

    abstract fun createPublicationTasks(publication: Publication, mavenPublication: MavenPublication): Iterable<String>

    @Suppress("SameParameterValue")
    protected fun checkPublicationField(value: Any?, field: String, fatal: Boolean) {
        if (value == null) {
            val message = "publisher.$field is not set."
            if (fatal) {
                throw IllegalArgumentException(message)
            } else {
                target.logger.log(LogLevel.WARN, message)
            }
        }
    }

    private var localProperties: Properties? = null

    protected fun findSecret(key: String): String? {
        // Try with environmental variable.
        val env: String? = System.getenv(key)
        if (!env.isNullOrEmpty()) return env
        // Try with findProperty.
        val project = target.findProperty(key) as? String
        if (!project.isNullOrEmpty()) return project
        // Try with local.properties file.
        if (localProperties == null) {
            val properties = Properties()
            val file = target.rootProject.file("local.properties")
            if (file.exists()) {
                val stream = FileInputStream(file)
                properties.load(stream)
            }
            localProperties = properties
        }
        val local = localProperties!!.getProperty(key)
        if (!local.isNullOrEmpty()) return local
        // We failed. Return null.
        return null
    }
}