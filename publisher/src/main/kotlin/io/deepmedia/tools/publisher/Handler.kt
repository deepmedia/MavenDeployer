package io.deepmedia.tools.publisher

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.publish.maven.MavenPublication
import java.io.FileInputStream
import java.util.*

internal abstract class Handler<P : Publication>(protected val target: Project) {

    abstract fun ownsPublication(name: String): Boolean

    abstract fun createPublication(name: String): P

    abstract fun fillPublication(publication: P)

    abstract fun checkPublication(publication: P)

    abstract fun createPublicationTasks(publication: P, mavenPublication: MavenPublication): Iterable<String>

    @Suppress("SameParameterValue")
    protected fun checkPublicationField(value: Any?, field: String, fatal: Boolean, extra: String = "") {
        if (value == null) {
            val message = "publisher.$field is not set. $extra"
            if (fatal) {
                require(false) { message }
            } else {
                target.logger.log(LogLevel.WARN, message)
            }
        }
    }
}