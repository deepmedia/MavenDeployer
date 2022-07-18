package io.deepmedia.tools.publisher.local

import io.deepmedia.tools.publisher.common.DefaultPublication

class LocalPublication(name: String) : DefaultPublication(name) {
    /**
     * Local maven directory.
     */
    var directory: String? = null
}