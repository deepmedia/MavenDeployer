package io.deepmedia.tools.publisher.bintray

import io.deepmedia.tools.publisher.Handler
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

internal class BintrayHandler(target: Project) : Handler<BintrayPublication>(target) {

    override fun ownsPublication(name: String) = false

    override fun createPublication(name: String) = BintrayPublication(name)

    override fun fillPublication(publication: BintrayPublication) = Unit

    override fun checkPublication(publication: BintrayPublication, fatal: Boolean) = Unit

    override fun createPublicationTask(publication: BintrayPublication, mavenPublication: MavenPublication) = ""
}