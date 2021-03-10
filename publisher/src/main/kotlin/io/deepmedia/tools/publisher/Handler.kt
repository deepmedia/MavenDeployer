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

    abstract fun checkPublication(publication: P, fatal: Boolean)

    abstract fun createPublicationTask(publication: P, mavenPublication: MavenPublication): String
}