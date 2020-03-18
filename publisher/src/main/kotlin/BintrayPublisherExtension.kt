package com.otaliastudios.tools.publisher

/**
 * Extension used by [BintrayPublisherPlugin].
 */
open class BintrayPublisherExtension : PublisherExtension() {

    class Auth : PublisherExtension.Auth() {
        var repo: String? = null
        var user: String? = null
        var key: String? = null
    }

    override val auth = Auth()
}