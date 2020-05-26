package com.otaliastudios.tools.publisher.deprecated

import com.otaliastudios.tools.publisher.deprecated.PublisherExtension

/**
 * Extension used by [LocalPublisherPlugin].
 */
open class LocalPublisherExtension : PublisherExtension() {

    /**
     * Local maven directory.
     */
    var directory: String? = null
}