package io.deepmedia.tools.publisher

import io.deepmedia.tools.publisher.bintray.BintrayPublication
import io.deepmedia.tools.publisher.bintray.BintrayPublicationHandler
import io.deepmedia.tools.publisher.common.DefaultPublication
import io.deepmedia.tools.publisher.local.LocalPublication
import io.deepmedia.tools.publisher.local.LocalPublicationHandler
import io.deepmedia.tools.publisher.sonatype.SonatypePublication
import io.deepmedia.tools.publisher.sonatype.SonatypePublicationHandler
import org.gradle.api.NamedDomainObjectContainer

open class PublisherExtension : Publication by DefaultPublication("default") {
    internal lateinit var publications: NamedDomainObjectContainer<Publication>
    internal lateinit var configuredPublications: NamedDomainObjectContainer<Publication>

    fun bintray(name: String = BintrayPublicationHandler.PREFIX, configure: BintrayPublication.() -> Unit)
            = add(BintrayPublicationHandler.PREFIX, name, configure)

    fun directory(name: String = LocalPublicationHandler.PREFIX, configure: LocalPublication.() -> Unit)
            = add(LocalPublicationHandler.PREFIX, name, configure)

    fun sonatype(name: String = SonatypePublicationHandler.PREFIX, configure: SonatypePublication.() -> Unit)
            = add(SonatypePublicationHandler.PREFIX, name, configure)

    private inline fun <reified P : Publication> add(prefix: String, name: String, configure: P.() -> Unit) {
        val publicationName = if (!name.startsWith(prefix)) {
            "${prefix}${name.capitalize()}"
        } else name
        val publication = publications.create(publicationName) as P
        configure.invoke(publication)
        configuredPublications.add(publication)
    }
}