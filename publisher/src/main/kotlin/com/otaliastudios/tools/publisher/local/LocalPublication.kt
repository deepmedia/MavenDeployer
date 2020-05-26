package com.otaliastudios.tools.publisher.local

import com.otaliastudios.tools.publisher.common.DefaultPublication

class LocalPublication(name: String) : DefaultPublication(name) {
    /**
     * Local maven directory.
     */
    var directory: String? = null
}