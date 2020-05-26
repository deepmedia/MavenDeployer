package com.otaliastudios.tools.publisher.bintray

import com.otaliastudios.tools.publisher.common.DefaultPublication

class BintrayPublication(name: String) : DefaultPublication(name) {
    var dryRun = false
    override val auth = BintrayAuth()
}