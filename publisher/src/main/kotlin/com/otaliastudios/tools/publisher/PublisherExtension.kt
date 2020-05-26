package com.otaliastudios.tools.publisher

import com.otaliastudios.tools.publisher.bintray.BintrayPublication
import com.otaliastudios.tools.publisher.bintray.BintrayPublicationHandler
import com.otaliastudios.tools.publisher.common.DefaultPublication
import com.otaliastudios.tools.publisher.local.LocalPublication
import com.otaliastudios.tools.publisher.local.LocalPublicationHandler
import org.gradle.api.NamedDomainObjectContainer

open class PublisherExtension : Publication by DefaultPublication("default") {
    internal lateinit var publications: NamedDomainObjectContainer<Publication>

    fun bintray(name: String = BintrayPublicationHandler.PREFIX, configure: BintrayPublication.() -> Unit)
            = add(BintrayPublicationHandler.PREFIX, name, configure)

    fun directory(name: String = LocalPublicationHandler.PREFIX, configure: LocalPublication.() -> Unit)
            = add(LocalPublicationHandler.PREFIX, name, configure)

    private inline fun <reified P : Publication> add(prefix: String, name: String, configure: P.() -> Unit) {
        val publicationName = if (!name.startsWith(prefix)) {
            "${prefix}${name.capitalize()}"
        } else name
        val publication = publications.create(publicationName) as P
        configure.invoke(publication)
    }
}