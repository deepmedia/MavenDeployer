package com.otaliastudios.tools.publisher.deprecated

import com.otaliastudios.tools.publisher.bintray.BintrayAuth

/**
 * Extension used by [BintrayPublisherPlugin].
 */
open class BintrayPublisherExtension : PublisherExtension() {

    override val auth = BintrayAuth()
}