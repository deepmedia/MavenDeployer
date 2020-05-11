package com.otaliastudios.tools.publisher

/**
 * Extension used by [LocalPublisherPlugin].
 */
open class LocalPublisherExtension : PublisherExtension() {

    /**
     * Local maven directory.
     */
    var directory: String? = null
}