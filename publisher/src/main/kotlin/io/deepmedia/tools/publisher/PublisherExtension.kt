package io.deepmedia.tools.publisher

import io.deepmedia.tools.publisher.bintray.BintrayPublication
import io.deepmedia.tools.publisher.bintray.BintrayHandler
import io.deepmedia.tools.publisher.common.DefaultPublication
import io.deepmedia.tools.publisher.local.LocalPublication
import io.deepmedia.tools.publisher.local.LocalHandler
import io.deepmedia.tools.publisher.sonatype.SonatypePublication
import io.deepmedia.tools.publisher.sonatype.SonatypeHandler
import org.gradle.api.NamedDomainObjectContainer

open class PublisherExtension : Publication by DefaultPublication("default") {
    internal lateinit var publications: NamedDomainObjectContainer<Publication>
    internal lateinit var configuredPublications: NamedDomainObjectContainer<Publication>

    fun bintray(name: String = "bintray", configure: BintrayPublication.() -> Unit = {}): Nothing
            = error("Bintray publications are not allowed anymore")

    fun directory(name: String = LocalHandler.PREFIX, configure: LocalPublication.() -> Unit = {})
            = add(LocalHandler.PREFIX, name, configure)

    fun sonatype(name: String = SonatypeHandler.PREFIX, configure: SonatypePublication.() -> Unit = {})
            = add(SonatypeHandler.PREFIX, name, configure)

    private inline fun <reified P : Publication> add(prefix: String, name: String, configure: P.() -> Unit) {
        val publicationName = if (!name.startsWith(prefix)) {
            "${prefix}${name.capitalize()}"
        } else name
        val publication = publications.create(publicationName) as P
        configure.invoke(publication)
        configuredPublications.add(publication)
    }
}